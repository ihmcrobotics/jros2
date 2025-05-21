<p align="center">
  <img src="media/jros2.png" width="30%" /><br>
  <a href="https://github.com/ihmcrobotics/jros2/wiki">Wiki</a>
  <a href="https://github.com/ihmcrobotics/jros2/issues">Issues</a>
</p>

-----------------
A ROS 2 library for Java. Uses Fast-DDS middleware. Fully compatible with other supported ROS 2 middlewares.

Fast-DDS version: `3.2.2`

ROS 2 compatible and tested distros: `[humble, jazzy]`

Supported platforms:
- Linux (Ubuntu 20.04+ or similar x86_64, arm64)
- (soon) Windows (Windows 10+ x86_64)
- (soon) macOS (macOS 13+ Intel, Apple Silicon)
- (soon) Android

## Features
- Fully compatible with ROS 2 humble or newer (may also work with older ROS 2 distros)
- Does not require a ROS 2 installation on the system
- Ready-to-use Java library, just add to your Maven or Gradle dependencies!
- Publish and subscribe to ROS 2 topics
- Supports custom message types
- Generate Java classes from .msg packages
- Fast-DDS backend
- Minimal and fast implementation
- Fully thread-safe
- Async and allocation-free API
- (soon) ROS 2 services
- (soon) ROS 2 actions
- (soon) ROS 2 parameters

## Usage
Read in-depth documentation on the [Wiki](https://github.com/ihmcrobotics/jros2/wiki)!

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
ROS2Publisher<Int32> intPublisher = node.createPublisher(topic);
Int32 message = new Int32();
message.setData(123);
intPublisher.publish(message);
```

Create a subscription to the intTopic:
```
ROS2Subscription<Int32> subscription = node.createSubscription(topic, reader -> {
  Int32 message = reader.read();
  // Do something with the message!
};
```

### Talker and listener example
Run a talker and listener example with `./run_talker_listener.sh`. If you have a local ROS 2 installation or use a ROS 2 container, you
can verify that ROS 2 is able to communicate with jros2.

```
[New shell]
jros2$ ./run_talker_listener.sh
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
