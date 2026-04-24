# jros2 Development Guide for LLMs

## Project Overview

**jros2** is a pure Java implementation of the ROS 2 (Robot Operating System 2) client library that uses Fast-DDS as its DDS middleware. It allows Java applications to communicate with ROS 2 systems without requiring a ROS 2 installation.

- **Repository**: https://github.com/ihmcrobotics/jros2
- **Wiki**: https://github.com/ihmcrobotics/jros2/wiki
- **Fast-DDS Version**: 3.2.2
- **Compatible ROS 2 Distros**: humble, jazzy, kilted
- **Supported Platforms**: Linux (x86_64, arm64, armhf), Windows (x86_64), macOS (Intel, Apple Silicon), Android (x86_64, arm64-v8a)

## Architecture

### Key Technologies

1. **Fast-DDS (eProsima Fast DDS)**: The underlying DDS implementation
   - Documentation: https://fast-dds.docs.eprosima.com/
   - Provides the real-time publish-subscribe middleware layer
   - Handles discovery, serialization, and network transport

2. **JavaCPP**: Used for JNI (Java Native Interface) bindings to Fast-DDS C++ libraries
   - Generates native code bindings automatically
   - Located in `src/native/fastddsjava.h`
   - Java bindings in `src/main/java/us/ihmc/fastddsjava/pointers/`

3. **ROS 2 Message Interface**: Auto-generates Java classes from `.msg` files
   - Generator code in `src/generator/`
   - Uses custom grammar and StringTemplate for code generation
   - Interfaces stored in `src/main/java-interfaces/`

### Project Structure

```
jros2/
  src/
    main/
      java/
        us/ihmc/jros2/              Core ROS2 Java API
        us/ihmc/fastddsjava/        FastDDS JNI bindings
      java-interfaces/              Generated message interfaces
      resources/
        fastddsjava/native/         Native libraries (.so, .dll, .dylib)
    native/
      fastddsjava.h                 C++ header with FastDDS bindings
    generator/                      Message interface generator
    parser/                         ROS .msg file parser
    test/                           Unit and integration tests
  ros2_interfaces/                  ROS2 message definitions (submodules)
  examples/                         Example applications
  cppbuild/                         C++ build artifacts (generated)
  build/                            Gradle build output
```

### Core Classes

#### ROS2Node (`ROS2Node.java`)
- Main entry point for creating ROS 2 nodes
- Manages Fast-DDS DomainParticipant lifecycle
- Creates publishers, subscriptions, services, actions, and parameters
- Thread-safe with ReadWriteLock for close operations
- Each node has its own DomainParticipant in Fast-DDS

**Key methods:**
- `createPublisher()` - Create a publisher for a topic
- `createSubscription()` - Subscribe to a topic with callback
- `createServiceClient()` / `createServiceServer()` - ROS 2 services
- `createActionClient()` / `createActionServer()` - ROS 2 actions
- `createParameter()` / `declareParameter()` - ROS 2 parameters

#### ROS2Publisher (`ROS2Publisher.java`)
- Publishes messages to ROS 2 topics
- Wraps Fast-DDS DataWriter
- CDR (Common Data Representation) serialization
- Statistics tracking built-in

#### ROS2Subscription (`ROS2Subscription.java`)
- Receives messages from ROS 2 topics
- Wraps Fast-DDS DataReader
- Callback-based API
- Allocation-free read methods available

#### ROS2Message (`ROS2Message.java`)
- Base interface for all ROS 2 message types
- Implements CDR serialization/deserialization
- Auto-generated from `.msg` files

#### ROS2Topic (`ROS2Topic.java`)
- Represents a ROS 2 topic with name and type
- Supports namespace manipulation (prepend/append/insert tokens)
- Topic names follow ROS 2 naming conventions (must start with `/`)

#### ROS2QoSProfile (`ROS2QoSProfile.java`)
- Quality of Service settings for publishers/subscriptions
- Maps to DDS QoS policies:
  - **Reliability**: RELIABLE, BEST_EFFORT
  - **Durability**: VOLATILE, TRANSIENT_LOCAL
  - **History**: KEEP_LAST, KEEP_ALL
  - **Liveliness**: AUTOMATIC, MANUAL_BY_TOPIC
  - **Deadline**, **Lifespan**, **Lease Duration**

## ROS 2 and DDS Concepts

### Topic Naming
- **ROS 2 Topics**: Start with `/` (e.g., `/chatter`, `/camera/image`)
- **DDS Topics**: ROS 2 topics are prefixed with `rt/` in DDS
  - ROS: `/chatter` → DDS: `rt/chatter`
  - Mapping defined in: https://design.ros2.org/articles/topic_and_service_names.html

### Service Naming
- **DDS Request Topic**: `rq<service_name>Request`
- **DDS Reply Topic**: `rr<service_name>Reply`

### Message Serialization
- Uses **CDR (Common Data Representation)** from OMG DDS specification
- Little-endian by default
- Implemented in `src/main/java/us/ihmc/fastddsjava/cdr/`

### Discovery Protocol
- ROS 2 uses DDS discovery to find nodes, topics, services
- Fast-DDS handles participant, publisher, and subscriber discovery automatically
- Discovery can be configured via:
  - `ROS_DOMAIN_ID` environment variable (0-232)
  - `ROS_AUTOMATIC_DISCOVERY_RANGE` environment variable
  - Fast-DDS XML profiles

## Building the Project

### Prerequisites
- JDK 17+
- CMake 3.16+
- C++ compiler (gcc/clang/MSVC)
- Git (for submodules)

### Build Native Libraries

The project includes build scripts for each platform:

```bash
# Linux x86_64
./build-linux-x86_64.bash

# Linux ARM64
./build-linux-arm64.bash

# Linux ARMHF
./build-linux-armhf.bash

# Windows x86_64
build-windows-x86_64.bat

# macOS (Intel/Apple Silicon)
./build-macos.bash

# Android
./build-android-arm64.bash
./build-android-x86_64.bash
```

**Important**: Always delete `cppbuild/` directory before rebuilding to ensure a clean build:
```bash
rm -rf cppbuild
./build-linux-x86_64.bash
```

### Build Process
1. Downloads and extracts Fast-CDR and Fast-DDS source
2. Builds Fast-DDS C++ libraries
3. Generates JavaCPP JNI bindings from `src/native/fastddsjava.h`
4. Compiles JNI native library (`libjnifastddsjava.so`)
5. Installs to `src/main/resources/fastddsjava/native/<platform>/`

### Gradle Build
```bash
# Build everything
./gradlew build

# Run tests
./gradlew test

# Run specific test
./gradlew test --tests ROS2PublishSubscribeTest.testROS2Subscription1

# Build without tests
./gradlew build -x test

# Clean build
./gradlew clean build
```

## Message Interface Generation

### Generating Java Classes from .msg Files

The project includes a Gradle plugin to generate Java interfaces from ROS 2 `.msg` files.

```bash
# Generate all default ROS 2 interfaces
./gradlew jros2GenerateDefaultInterfaces

# Generate from custom package
./gradlew jros2Generate -PpackagePath=/path/to/ros_package
```

### Message Definition Format

ROS 2 `.msg` files use a simple syntax:

```
# Comments start with #
int32 x
int32 y
string label
float64[] measurements
Header header  # From std_msgs
```

**Primitive types:**
- `bool`, `int8`, `uint8`, `int16`, `uint16`, `int32`, `uint32`, `int64`, `uint64`
- `float32`, `float64`
- `string`, `wstring`
- `char`, `byte`

**Array types:**
- Fixed: `int32[10]`
- Dynamic: `int32[]`
- Bounded: `string<=256`

### Adding New ROS 2 Interface Packages

1. Add as git submodule in `ros2_interfaces/`:
```bash
cd ros2_interfaces
git submodule add -b humble https://github.com/ros2/common_interfaces.git
```

2. Update `build.gradle.kts` to include the package path:
```kotlin
packagePaths = listOf(
    projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("std_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("my_new_package").absolutePath,
)
```

3. Generate interfaces:
```bash
./gradlew jros2GenerateDefaultInterfaces
```

## Common Development Tasks

### Adding a New Native Function

1. **Add C++ function to `src/native/fastddsjava.h`:**
```cpp
// Returns eprosima::fastdds::dds::SomeType*
void* fastddsjava_some_function(void* participant_, int param) {
    eprosima::fastdds::dds::DomainParticipant* participant =
        static_cast<eprosima::fastdds::dds::DomainParticipant*>(participant_);

    // Implementation...
    return result;
}
```

2. **Add JNI binding to `src/main/java/us/ihmc/fastddsjava/pointers/fastddsjava.java`:**
```java
public static native Pointer fastddsjava_some_function(Pointer participant_, int param);
```

3. **Rebuild native library:**
```bash
rm -rf cppbuild
./build-linux-x86_64.bash
```

4. **Use in Java code:**
```java
Pointer result = fastddsjava.fastddsjava_some_function(participant, 42);
```

### Adding Support for a New QoS Policy

1. **Add field to `ROS2QoSProfile.java`:**
```java
private SomePolicy somePolicy;

public void somePolicy(SomePolicy policy) {
    this.somePolicy = policy;
}

public SomePolicy getSomePolicy() {
    return somePolicy;
}
```

2. **Update `QoSTools.java` to translate to Fast-DDS XML:**
```java
if (qosProfile.getSomePolicy() != null) {
    // Add to XML profile
}
```

3. **Test with publisher/subscription**

### Implementing a New ROS 2 Feature

When adding ROS 2 services, actions, or parameters:

1. **Study the rmw_fastrtps implementation:**
   - Clone: https://github.com/ros2/rmw_fastrtps (humble branch)
   - Understand how it maps ROS 2 concepts to DDS

2. **Check Fast-DDS documentation:**
   - https://fast-dds.docs.eprosima.com/

3. **Follow existing patterns:**
   - Services: See `ROS2ServiceClient.java`, `ROS2ServiceServer.java`
   - Actions: See `ROS2ActionClient.java`, `ROS2ActionServer.java`
   - Parameters: See `ROS2ParameterNode.java`

## Testing

### Running Tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests ROS2PublishSubscribeTest

# Specific test method
./gradlew test --tests ROS2PublishSubscribeTest.testROS2Subscription1

# With ROS 2 integration (requires ROS 2 installed)
export ROS_DISTRO=jazzy
./gradlew test
```

### Test Structure

- Unit tests in `src/test/java/us/ihmc/jros2/`
- Integration tests may require ROS 2 installation
- Tests use domain ID 42 by default to avoid conflicts

### Writing Tests

```java
@Test
public void testPublishSubscribe() throws InterruptedException {
    ROS2Node node = new ROS2Node("test_node", 42); // Use test domain

    ROS2Topic<String> topic = new ROS2Topic<>("/test", String.class);
    ROS2Publisher<String> pub = node.createPublisher(topic);

    final String[] received = {null};
    node.createSubscription(topic, reader -> {
        received[0] = reader.read().getDataAsString();
    });

    String msg = new String();
    msg.setDataAsString("test");
    pub.publish(msg);

    Thread.sleep(100); // Allow time for delivery

    assertEquals("test", received[0]);
    node.close();
}
```

## Debugging Tips

### Enable Fast-DDS Logging

Set environment variables:
```bash
export FASTDDS_LOG_LEVEL=info  # or warning, error
export FASTDDS_TRACE_LEVEL=debug
```

### Common Issues

1. **Native library not found**
   - Ensure build completed successfully
   - Check `src/main/resources/fastddsjava/native/<platform>/`
   - Verify `java.library.path` includes the native directory

2. **Type mismatch between publisher and subscription**
   - Ensure both use the same message type
   - Check DDS type names match exactly
   - Regenerate interfaces if .msg files changed

3. **Topics not discovered**
   - Check `ROS_DOMAIN_ID` matches on both sides
   - Verify network/firewall settings allow multicast
   - Check QoS compatibility (reliability, durability must match)

4. **Crashes (SIGABRT, SIGSEGV)**
   - Usually indicates native code issue
   - Check for null pointers passed to JNI
   - Verify Pointer objects aren't garbage collected prematurely
   - Use `BytePointer` with `long` parameter: `new BytePointer(24L)` not `new BytePointer(24)`

5. **Memory leaks**
   - Always call `.close()` on Pointer objects
   - Use try-with-resources for automatic cleanup
   - Call `node.close()` when done

## ROS 2 References

### Essential Documentation

1. **ROS 2 Design Documents**
   - Topic and Service Names: https://design.ros2.org/articles/topic_and_service_names.html
   - QoS Policies: https://docs.ros.org/en/rolling/Concepts/Intermediate/About-Quality-of-Service-Settings.html
   - Node to Participant Mapping: https://design.ros2.org/articles/Node_to_Participant_mapping.html

2. **Fast-DDS Documentation**
   - Getting Started: https://fast-dds.docs.eprosima.com/en/latest/fastdds/getting_started/getting_started.html
   - DDS Layer: https://fast-dds.docs.eprosima.com/en/latest/fastdds/dds_layer/dds_layer.html
   - XML Profiles: https://fast-dds.docs.eprosima.com/en/latest/fastdds/xml_configuration/xml_configuration.html

3. **ROS 2 Concepts**
   - Discovery: https://docs.ros.org/en/rolling/Concepts/Basic/About-Discovery.html
   - Nodes: https://docs.ros.org/en/rolling/Tutorials/Beginner-CLI-Tools/Understanding-ROS2-Nodes/Understanding-ROS2-Nodes.html
   - Topics: https://docs.ros.org/en/rolling/Tutorials/Beginner-CLI-Tools/Understanding-ROS2-Topics/Understanding-ROS2-Topics.html
   - Services: https://docs.ros.org/en/rolling/Tutorials/Beginner-CLI-Tools/Understanding-ROS2-Services/Understanding-ROS2-Services.html
   - Actions: https://docs.ros.org/en/rolling/Tutorials/Beginner-CLI-Tools/Understanding-ROS2-Actions/Understanding-ROS2-Actions.html
   - Parameters: https://docs.ros.org/en/rolling/Tutorials/Beginner-CLI-Tools/Understanding-ROS2-Parameters/Understanding-ROS2-Parameters.html

### ROS 2 Message Packages

Common ROS 2 interface repositories (as git submodules in `ros2_interfaces/`):

- **rcl_interfaces**: Core ROS 2 interfaces (builtin_interfaces, action_msgs, lifecycle_msgs, etc.)
  - https://github.com/ros2/rcl_interfaces

- **common_interfaces**: Standard message types (std_msgs, geometry_msgs, sensor_msgs, etc.)
  - https://github.com/ros2/common_interfaces

- **example_interfaces**: Simple example messages for testing
  - https://github.com/ros2/example_interfaces

## Code Style and Conventions

### Java Code Style

- Use IHMC's Java style (similar to Google Java Style)
- 3-space indentation
- Opening brace on same line
- Descriptive variable names
- JavaDoc for public APIs

### Native Code Style

- C++11 or newer
- Use `static_cast<>` for C++ casts
- Clean up resources properly (delete, deallocate)
- Return `void*` for opaque pointers to Java

### Naming Conventions

- **Java Classes**: `PascalCase` (e.g., `ROS2Node`)
- **Java Methods**: `camelCase` (e.g., `createPublisher`)
- **Native Functions**: `fastddsjava_snake_case` (e.g., `fastddsjava_create_topic`)
- **Message Classes**: Follow ROS 2 convention (e.g., `std_msgs.String`, `geometry_msgs.Twist`)

## Performance Considerations

### Zero-Copy and Allocation-Free Patterns

jros2 provides allocation-free APIs where possible:

```java
// Allocation-free read (reuses message object)
subscription = node.createSubscription(topic, reader -> {
    reader.read(reusableMessage);  // No allocation
    // Process reusableMessage
});

// Regular read (allocates new message each time)
subscription = node.createSubscription(topic, reader -> {
    Message msg = reader.read();  // Allocates
});
```

### CDR Serialization

- Uses Fast-DDS built-in CDR serialization
- Little-endian by default
- Optimized for real-time performance
- Buffer reuse minimizes allocations

### Thread Safety

- All public APIs are thread-safe
- Internal use of `ReadWriteLock` for safe concurrent access
- Publishers and subscriptions can be used from multiple threads

## Advanced Topics

### Custom Transport Configuration

Configure Fast-DDS transports via `jros2.properties`:

```properties
# Limit to specific network interfaces
us.ihmc.jros2.interfaceWhitelist=192.168.1.0/24,10.0.0.0/8

# Or use environment variable
export JROS2_INTERFACE_WHITELIST=192.168.1.0/24
```

### XML Profile Configuration

Fast-DDS supports XML configuration for advanced QoS and transport settings:

```java
// Load XML profile
ProfilesXML profilesXML = new ProfilesXML();
ParticipantProfileType profile = new ParticipantProfileType();
profile.setDomainId(42);
// Configure profile...
profilesXML.addParticipantProfile(profile);
profilesXML.load();
```

See: https://fast-dds.docs.eprosima.com/en/latest/fastdds/xml_configuration/xml_configuration.html

### Domain ID Ranges

- ROS 2 Domain IDs: 0-232 (ROS 2 spec limitation)
- DDS Domain IDs: 0-232 (Fast-DDS uses same range)
- Default domain: 0
- Test domain (recommended): 42 or higher to avoid conflicts

## Troubleshooting Guide

### Native Library Build Issues

**Problem**: Build fails with "CMake not found"
```bash
# Install CMake
sudo apt install cmake  # Ubuntu/Debian
brew install cmake      # macOS
```

**Problem**: Build fails with compiler errors
- Ensure C++11 or newer compiler
- Check Fast-DDS version compatibility (3.2.2)
- Delete `cppbuild/` and retry

**Problem**: JNI generation fails
- Ensure JavaCPP is installed correctly
- Check `src/native/fastddsjava.h` syntax
- Verify function signatures match Java declarations

### Runtime Issues

**Problem**: `UnsatisfiedLinkError` - native method not found
- Rebuild native library: `rm -rf cppbuild && ./build-linux-x86_64.bash`
- Check JNI function name matches exactly (case-sensitive)
- Verify native library was copied to resources directory

**Problem**: No communication between nodes
- Check domain IDs match
- Verify QoS settings are compatible
- Check network/firewall (multicast required for discovery)
- Enable Fast-DDS logging to see discovery messages

**Problem**: Slow discovery or missed messages
- Increase discovery time (wait longer after node creation)
- Check QoS durability settings (TRANSIENT_LOCAL for late-joiners)
- Verify multicast is working on network

## Contributing

When contributing to jros2:

1. Follow existing code style and patterns
2. Add tests for new features
3. Update documentation (README, Wiki, JavaDoc)
4. Ensure all platforms build successfully
5. Test against vanilla ROS 2 for compatibility
6. Keep Fast-DDS version synchronized

## Getting Help

- **GitHub Issues**: https://github.com/ihmcrobotics/jros2/issues
- **GitHub Wiki**: https://github.com/ihmcrobotics/jros2/wiki
- **ROS 2 Discourse**: https://discourse.ros.org/
- **Fast-DDS GitHub**: https://github.com/eProsima/Fast-DDS

## Quick Reference

### Environment Variables

- `ROS_DOMAIN_ID` - ROS 2 domain (0-232, default: 0)
- `ROS_DISTRO` - ROS 2 distribution name (humble, jazzy, etc.)
- `JROS2_INTERFACE_WHITELIST` - Network interface filter
- `FASTDDS_LOG_LEVEL` - Fast-DDS log level (info, warning, error)

### Common Commands

```bash
# Build everything
./gradlew build

# Run example
./run_talker_listener.sh

# Generate interfaces
./gradlew jros2GenerateDefaultInterfaces

# Run tests with ROS 2
export ROS_DISTRO=jazzy
./gradlew test

# Build native library
rm -rf cppbuild
./build-linux-x86_64.bash
```

### Useful Gradle Tasks

```bash
./gradlew tasks                    # List all tasks
./gradlew test                     # Run tests
./gradlew build                    # Build project
./gradlew clean                    # Clean build
./gradlew publishToMavenLocal      # Install locally
./gradlew javadoc                  # Generate JavaDoc
```

---

**Last Updated**: 2024-04-24  
**jros2 Version**: Development (service-actions-params branch)  
**Fast-DDS Version**: 3.2.2
