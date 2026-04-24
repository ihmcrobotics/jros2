# jros2 Parameter Services - Implementation Summary

## Current Status

Successfully implemented ROS2 parameter services and discovery callbacks to eliminate sleeps in tests. The implementation is **mostly complete** but there's an issue with the discovery callbacks that needs debugging.

## What Was Accomplished

### 1. Fixed IDLStringSequence Deserialization Bug (CRITICAL FIX)
**File**: `src/main/java/us/ihmc/fastddsjava/cdr/idl/IDLStringSequence.java`

**Problem**: NullPointerException when deserializing GetParameters_Request - StringBuilder array elements were null.

**Fix**: Initialize StringBuilder objects in `readElement()` before using them:
```java
@Override
public void readElement(CDRBuffer buffer)
{
   // Initialize StringBuilder if needed
   if (elements[position] == null)
   {
      int capacity = defaultStringLength > 0 ? defaultStringLength : DEFAULT_MAX_STRING_LENGTH;
      elements[position] = new StringBuilder(capacity);
   }

   StringBuilder element = elements[position++];
   buffer.readString(element);
}
```

Also fixed `elementSizeBytes()` to include 4-byte length prefix + string data + 1-byte null terminator per OMG CDR specification.

### 2. ROS2 Service Topic Naming
**File**: `src/main/java/us/ihmc/jros2/ROS2Node.java`

Fixed service topic naming to use correct DDS prefixes and suffixes:
- Service topics use "rq"/"rr" prefixes instead of "rt"
- Service suffixes changed from "Request"/"Response" to "Request"/"Reply"
- Added prefix detection in `getOrCreateTopicData()` based on topic name suffix

```java
String topicName = topic.getName();
String prefix;
if (topicName.endsWith("Request"))
{
   prefix = "rq";
}
else if (topicName.endsWith("Reply"))
{
   prefix = "rr";
}
else
{
   prefix = "rt";
}
```

### 3. Lazy Parameter Service Initialization
**File**: `src/main/java/us/ihmc/jros2/ROS2Node.java`

Parameter services are now created lazily on first `declareParameter()` call:
```java
private void ensureParameterServicesInitialized()
{
   if (parameterService == null && !closed)
   {
      synchronized (this)
      {
         if (parameterService == null && !closed)
         {
            parameterService = new ROS2ParameterService(this);
         }
      }
   }
}
```

### 4. Discovery Callbacks (NEW - IN PROGRESS)

**Goal**: Eliminate Thread.sleep() calls in tests by using DDS subscription matched callbacks.

**C++ Header** (`src/native/fastddsjava.h`): **NO CHANGES NEEDED**
- The `on_subscription_matched` callback already exists (lines 122-126)
- Already exposed to Java via `set_on_subscription_callback()`

**Added to ROS2Subscription.java**:
```java
void setOnSubscriptionMatchedCallback(Runnable callback)
{
   us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnSubscriptionCallback fastddsCallback =
      new us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnSubscriptionCallback()
   {
      @Override
      public void call()
      {
         if (callback != null)
         {
            callback.run();
         }
      }
   };
   listener.set_on_subscription_callback(fastddsCallback);
}
```

**Added to ROS2ServiceClient.java**:
```java
private volatile boolean serverDiscovered;

// In constructor:
responseSubscription.setOnSubscriptionMatchedCallback(() -> serverDiscovered = true);

public boolean waitForServer(long timeoutMs)
{
   long startTime = System.currentTimeMillis();
   while (!serverDiscovered && !closed)
   {
      if (System.currentTimeMillis() - startTime > timeoutMs)
      {
         return false;
      }
      try
      {
         Thread.sleep(10);
      }
      catch (InterruptedException e)
      {
         Thread.currentThread().interrupt();
         return false;
      }
   }
   return serverDiscovered;
}
```

**Added to ROS2ParameterClient.java**:
```java
public boolean waitForServer(long timeoutMs)
{
   return getParametersClient.waitForServer(timeoutMs);
}
```

**Updated ROS2ParameterTest.java**:
- Added 3 new parameter client tests using `waitForServer()` instead of `Thread.sleep()`
- Tests: `testParameterClientGetParameter`, `testParameterClientSetParameter`, `testParameterClientGetMultipleParameters`
- Each test calls `client.waitForServer(5000)` instead of sleeping

## Current Problem

**The discovery callback is not firing** - tests hang waiting for server discovery. The `serverDiscovered` flag never becomes true even though parameter services are created.

**Possible causes**:
1. The callback might not be set up correctly in the listener
2. The callback might be getting garbage collected
3. FastDDS might not be calling `on_subscription_matched` for some reason
4. The timing of when we set the callback vs when the subscription is created might be wrong

## Test Results

### Java-Only Tests (WITHOUT discovery callbacks)
**26/26 ROS2ParameterTest tests PASSING** (before adding discovery callback tests)
- All parameter types work correctly
- Parameter events publish correctly
- Multi-node parameter access works

### Service Tests
**8/8 ROS2ServiceTest tests PASSING**
- Regular AddTwoInts services work fine

### Integration Tests
**Java parameter test runs successfully** but Python interop has service type metadata issues.

**Issue discovered**: `ros2 service list -t` shows empty types `[]` for Java services:
```
/java_parameter_test/get_parameters []
```

Should show:
```
/java_parameter_test/get_parameters [rcl_interfaces/srv/GetParameters]
```

**Warnings in FastRTPS**:
```
[WARN] service type contains 'dds_::' but does not have a suffix, report this: 'rcl_interfaces::msg::dds_::GetParametersRequest_'
```

This is a **fundamental jros2 limitation** - service type metadata isn't properly registered with DDS/FastRTPS, preventing Python/C++ clients from discovering Java services through standard ROS2 discovery. Services work Java-to-Java but not for cross-language interop.

## Files Modified

### Core Implementation
1. **src/main/java/us/ihmc/fastddsjava/cdr/idl/IDLStringSequence.java** - Fixed deserialization bug
2. **src/main/java/us/ihmc/jros2/ROS2Node.java** - Service naming, lazy init, prefix detection
3. **src/main/java/us/ihmc/jros2/ROS2ParameterService.java** - Parameter service handlers (removed debug)
4. **src/main/java/us/ihmc/jros2/ROS2ServiceClient.java** - Removed debug output
5. **src/main/java/us/ihmc/jros2/ROS2ServiceServer.java** - Removed debug output

### Discovery Callbacks (NEW)
6. **src/main/java/us/ihmc/jros2/ROS2Subscription.java** - Added `setOnSubscriptionMatchedCallback()`
7. **src/main/java/us/ihmc/jros2/ROS2ServiceClient.java** - Added discovery flag and `waitForServer()`
8. **src/main/java/us/ihmc/jros2/ROS2ParameterClient.java** - Added `waitForServer()` wrapper

### Tests
9. **src/test/java/us/ihmc/jros2/ROS2ParameterTest.java** - Added 3 new tests using waitForServer
10. **src/test/integration-tests/ros2_workspace/src/jros2_interop_tests/scripts/java_parameter_test** - Fixed classpath

### Integration Test Dependencies
Fixed classpath for Java-Python interop by copying all JARs from parent `build/libs/` to integration test workspace. Required JARs include:
- javacpp-1.5.11-ihmc-2.jar
- ihmc-native-library-loader-2.0.6.jar
- jackson-databind, jackson-core, jackson-annotations
- jackson-dataformat-xml
- jackson-module-jaxb
- stax2-api
- woodstox

## Next Steps to Continue

### 1. Debug Discovery Callbacks (IMMEDIATE PRIORITY)

The discovery callback is not firing. Need to investigate:

**Check if callback is being set before or after subscription creation**:
The callback is set AFTER the subscription is created in `ROS2ServiceClient` constructor. This might be the issue - if the server is already available when we set the callback, we might miss the initial match event.

**Try this fix**:
```java
// In ROS2Subscription constructor, BEFORE creating the datareader:
private Runnable subscriptionMatchedCallback = null;

void setOnSubscriptionMatchedCallback(Runnable callback)
{
   this.subscriptionMatchedCallback = callback;
   // Create the FastDDS callback wrapper
   us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnSubscriptionCallback fastddsCallback =
      new us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnSubscriptionCallback()
   {
      @Override
      public void call()
      {
         if (subscriptionMatchedCallback != null)
         {
            subscriptionMatchedCallback.run();
         }
      }
   };
   listener.set_on_subscription_callback(fastddsCallback);
}

// In constructor, set the callback on listener BEFORE creating datareader:
listener.set_on_subscription_callback(subscriptionMatchedCallbackImpl);
fastddsDataReader = fastddsjava_create_datareader(fastddsSubscriber, topicData.fastddsTopic, listener, subscriberProfileName);
```

**Or simpler approach**: Just revert to using Thread.sleep() for now since the sleeps are minimal (100-500ms) and discovery callbacks are complex.

### 2. Verify All Tests Pass

Run full test suite:
```bash
export ROS_DISTRO=jazzy && ./gradlew test --tests ROS2ParameterTest
```

### 3. Consider Service Type Metadata Issue

The service type metadata problem is a separate, larger issue. For now, document that:
- Java-to-Java services work perfectly
- Java-to-Python/C++ service interop doesn't work due to missing type metadata in DDS registration
- This is a known limitation of jros2's service implementation

This would require significant changes to how services are registered with FastDDS to include proper type support information.

## Important Code Locations

### Service Creation
- **ROS2Node.createServiceServer()** - Line 720
- **ROS2Node.createServiceClient()** - Line 663
- Service topic names: `serviceName + "Request"` and `serviceName + "Reply"`

### Parameter Services
- **ROS2ParameterService** - Creates 6 parameter services per node
  - get_parameters
  - set_parameters
  - set_parameters_atomically
  - list_parameters
  - describe_parameters
  - get_parameter_types

### Discovery Callbacks
- **fastddsjava.h** - Line 113: `set_on_subscription_callback()`
- **fastddsjava.h** - Line 122: `on_subscription_matched()` callback implementation
- **ROS2Subscription** - `setOnSubscriptionMatchedCallback()` method
- **ROS2ServiceClient** - `waitForServer()` method

## Git Status

Modified files:
```
src/main/java/us/ihmc/fastddsjava/cdr/idl/IDLStringSequence.java
src/main/java/us/ihmc/jros2/ROS2Node.java
src/main/java/us/ihmc/jros2/ROS2ParameterService.java
src/main/java/us/ihmc/jros2/ROS2ServiceClient.java
src/main/java/us/ihmc/jros2/ROS2ServiceServer.java
src/main/java/us/ihmc/jros2/ROS2Subscription.java
src/main/java/us/ihmc/jros2/ROS2ParameterClient.java
src/test/java/us/ihmc/jros2/ROS2ParameterTest.java
src/test/integration-tests/ros2_workspace/src/jros2_interop_tests/scripts/java_parameter_test
```

Current branch: `service-actions-params`

## Key Insights

1. **Parameters use normal ROS2 services** - The user correctly identified this, which led to finding the real bug in IDLStringSequence deserialization.

2. **Discovery callbacks already exist in C++** - No C++ header changes needed, just use existing `on_subscription_matched` callback.

3. **Service type metadata is broken** - This is a broader jros2 issue affecting all services, not specific to parameters. Would require major refactoring to fix.

4. **Lazy initialization works well** - Parameter services are created on-demand without any sleeps or threads, as requested by the user.

## Recommended Next Action

**Option A** (Simple): Revert discovery callback changes and just use Thread.sleep() in tests. The sleeps are minimal and tests work fine.

**Option B** (Debug): Fix the discovery callback timing issue by ensuring the callback is set on the listener BEFORE the datareader is created, or by storing the callback and checking subscription status immediately after setting it.

**Option C** (Hybrid): Keep discovery callbacks for future use but also use short sleeps as fallback to ensure tests pass reliably.

I recommend **Option B** - debug and fix the discovery callbacks properly since the infrastructure is already in place and working callbacks would be valuable for future development.
