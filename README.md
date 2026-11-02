<p align="center">
  <img src="media/jros2.png" width="30%" /><br>
  <a href="https://github.com/ihmcrobotics/jros2/wiki">Wiki</a>
  <a href="https://github.com/ihmcrobotics/jros2/issues">Issues</a>
</p>
<p align="center">
  <a href="https://www.paypal.com/donate/?hosted_button_id=DH22GB8B6TD84">Donate here to fund open-source robotics projects!</a>
</p>

-----------------

A ROS 2 library for Java. Uses Fast-DDS middleware. Fully compatible with other supported ROS 2 DDS middlewares.

Fast-DDS version: `3.6.2`

ROS 2 compatible and tested distros: `[foxy, humble, jazzy, kilted, lyrical]`

Supported platforms:
- Linux (Ubuntu 20.04+ or similar x86_64, arm64, armhf)
- Windows (Windows 10+ x86_64, ARM64)
- macOS (macOS 13+ Intel, Apple Silicon)
- Android (Android 12+ x86_64, arm64-v8a, armeabi-v7a)

Works on NVIDIA Jetson and all versions of Raspberry Pi!

## Features
- Fully compatible with ROS 2 foxy or newer
- Does not require a ROS 2 installation on the system
- Ready-to-use Java library, just add to your Maven or Gradle dependencies!
- Publish and subscribe to ROS 2 topics
- ROS 2 services (servers and clients)
- ROS 2 actions (servers and clients)
- ROS 2 parameters (with the standard parameter services and `/parameter_events`)
- Supports custom message types
- Generate Java classes from ROS 2 .msg, .srv and .action files
- Fast-DDS backend
- Minimal and fast implementation
- Fully thread-safe
- Async and allocation-free API (topics)

### Services, actions and parameters compatibility
Services, actions and parameters follow the same protocol as `rmw_fastrtps_cpp` (the default ROS 2 middleware) and are tested
against rclcpp and rclcpp_action nodes in both directions (see [Interoperability tests](#interoperability-tests)).

| | Status |
|---|---|
| ROS 2 distros | Tested with foxy, humble, jazzy and lyrical. kilted uses the same protocol and is expected to work |
| ROS 2 nodes using `rmw_fastrtps_cpp` | Tested |
| ROS 2 nodes using `rmw_connextdds` | Not tested. Expected to work with its default (extended) request/reply mapping |
| ROS 2 nodes using `rmw_cyclonedds_cpp` | Not supported: Cyclone DDS identifies requests with a header in the payload |
| Platforms | All supported platforms. Tested in CI on Linux x86_64, macOS (ARM and Intel) and Windows x86_64; on Android, linux-arm64/armhf and Windows ARM64 the native libraries are built but services, actions and parameters are not run in CI |

jros2 nodes are not announced in the ROS 2 graph, so `ros2 node list`, `ros2 param list` and other commands that look nodes up
by name do not see them. Clients that use the service names directly (e.g. `rclcpp::SyncParametersClient`, `ros2 service call`)
work.

## Usage
Read in-depth documentation on the [Wiki](https://github.com/ihmcrobotics/jros2/wiki)!

### Gradle

Add the repository in `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://robotlabfiles.ihmc.us/repository/")
        }
    }
}
```

Add the dependency in `build.gradle.kts`:
```kotlin
dependencies {
  implementation("us.ihmc:jros2:1.5.1")
}
```

### Maven

Add the repository in `pom.xml`:
```xml
<repositories>
  <repository>
    <id>ihmc-repo</id>
    <url>https://robotlabfiles.ihmc.us/repository/</url>
  </repository>
</repositories>
```

Add the dependency:
```xml
<dependencies>
  <dependency>
    <groupId>us.ihmc</groupId>
    <artifactId>jros2</artifactId>
    <version>1.5.1</version>
  </dependency>
</dependencies>
```

### Android

jros2 fully supports Android! Add the repository and dependency in your Android project:

Add the repository in `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://robotlabfiles.ihmc.us/repository/")
        }
    }
}
```

Add the dependency in your app's `build.gradle.kts`:
```kotlin
dependencies {
    implementation("us.ihmc:jros2-android:1.5.1")
}
```

See the [Android Support Wiki](https://github.com/ihmcrobotics/jros2/wiki/Android-Support) for complete setup instructions and examples.

### Here's the basics:

Create a ROS2Node:
```
int domainId = 100;
ROS2Node node = new ROS2Node("my_node", domainId); // domainId is 0 by default
// Call node.close() when you're done with the node!
```

Create a ROS2Topic:
```
ROS2Topic<Int32> intTopic = new ROS2Topic("/int_topic", Int32.class);
```

Create a ROS2Publisher and publish a message on the intTopic:
```
ROS2Publisher<Int32> intPublisher = node.createPublisher(intTopic);
Int32 message = new Int32();
message.setData(123);
intPublisher.publish(message);
```

Create a subscription to the intTopic:
```
ROS2Subscription<Int32> subscription = node.createSubscription(intTopic, reader -> {
  Int32 message = reader.read();
  // Do something with the message!
};
```

### Services
A service `Foo.srv` is generated as two messages, `Foo_Request` and `Foo_Response`.

Create a service server. The callback fills the response, which is sent when it returns:
```
node.createService("/add_two_ints", AddTwoInts_Request.class, AddTwoInts_Response.class, (request, response) -> {
  response.setSum(request.getA() + request.getB());
});
```

Create a client and call the service:
```
ROS2Client<AddTwoInts_Request, AddTwoInts_Response> client = node.createClient("/add_two_ints", AddTwoInts_Request.class, AddTwoInts_Response.class);
client.waitForService(5000);

AddTwoInts_Request request = new AddTwoInts_Request();
request.setA(2);
request.setB(3);
AddTwoInts_Response response = client.sendRequest(request).get(5, TimeUnit.SECONDS); // sendRequest returns a CompletableFuture
```

### Parameters
Declaring the first parameter starts the node's parameter services (`/<node name>/get_parameters`, `set_parameters`, ...), so
other ROS 2 nodes can read and change them:
```
node.declareParameter("rate", 10);
node.declareParameter("frame_id", "map", new ROS2ParameterDescriptor("Frame of the output", true, false)); // read-only

long rate = node.getParameter("rate").asLong();
ROS2SetParametersResult result = node.setParameter("rate", 20); // result.successful(), result.reason()

// Validate changes, local or remote, before they are applied
node.addOnSetParametersCallback(parameters -> {
  for (ROS2Parameter parameter : parameters)
    if (parameter.getName().equals("rate") && parameter.asLong() <= 0)
      return "rate must be positive";
  return null; // accept
});
```
As in rclcpp, parameters must be declared before they are set, and their type cannot change unless they are declared with
dynamic typing.

### Actions
An action `Foo.action` is generated as `Foo_Goal`, `Foo_Result`, `Foo_Feedback` and the messages ROS 2 uses internally for it.

Create an action server:
```
node.createActionServer("/fibonacci", Fibonacci_Goal.class, Fibonacci_Result.class, Fibonacci_Feedback.class, new ROS2ActionServerCallbacks<>() {
  @Override
  public ROS2GoalResponse handleGoal(UUID goalId, Fibonacci_Goal goal) {
    return goal.getOrder() < 0 ? ROS2GoalResponse.REJECT : ROS2GoalResponse.ACCEPT_AND_EXECUTE;
  }

  @Override
  public ROS2CancelResponse handleCancel(ROS2ServerGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> goalHandle) {
    return ROS2CancelResponse.ACCEPT;
  }

  @Override
  public void handleAccepted(ROS2ServerGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> goalHandle) {
    // Runs on the action server's own thread pool, so it can block until the goal is done
    Fibonacci_Feedback feedback = new Fibonacci_Feedback();
    // ... compute, checking goalHandle.isCanceling() and calling goalHandle.publishFeedback(feedback)
    Fibonacci_Result result = new Fibonacci_Result();
    goalHandle.succeed(result); // or abort(result), or canceled(result) after a cancel request was accepted
  }
});
```

Create an action client and send a goal:
```
ROS2ActionClient<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> client =
      node.createActionClient("/fibonacci", Fibonacci_Goal.class, Fibonacci_Result.class, Fibonacci_Feedback.class);
client.waitForActionServer(5000);

Fibonacci_Goal goal = new Fibonacci_Goal();
goal.setOrder(10);
ROS2ClientGoalHandle<Fibonacci_Goal, Fibonacci_Result, Fibonacci_Feedback> goalHandle =
      client.sendGoal(goal, (handle, feedback) -> System.out.println("Feedback: " + feedback)).get();

if (goalHandle.isAccepted()) {
  ROS2ActionResult<Fibonacci_Result> result = goalHandle.getResultFuture().get(); // result.status(), result.result()
  // goalHandle.cancel() requests the server to cancel the goal
}
```

### Talker and listener example
Run a talker and listener example with `./scripts/run_talker_listener.sh`. If you have a local ROS 2 installation or use a ROS 2 container, you
can verify that ROS 2 is able to communicate with jros2.

```
[New shell]
jros2$ ./scripts/run_talker_listener.sh
> Task :examples:ros2-ros2-talker-listener:run
Publishing: 'Hello world: 0'
Publishing: 'Hello world: 1'
I heard: 'Hello world: 1'
Publishing: 'Hello world: 2'
I heard: 'Hello world: 2'
[...]
```
```
[New shell]
jros2$ source /opt/ros/humble/setup.bash 
jros2$ ros2 topic echo /chatter
data: 'Hello world: 1'
---
data: 'Hello world: 2'
---
[...]
```

### Interoperability tests
`ROS2InteropTest` runs services, parameters and actions against rclcpp and rclcpp_action nodes (from
`src/test/ros2/jros2_interop`). With Docker, run it against any ROS 2 distro using the official `ros:<distro>` image:
```
jros2$ src/test/ros2/run-interop-docker.sh lyrical
```
See [src/test/ros2/jros2_interop/README.md](src/test/ros2/jros2_interop/README.md) to run it with a local ROS 2 workspace.
