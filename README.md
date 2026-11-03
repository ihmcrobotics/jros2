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

ROS 2 compatible and tested distros: `[foxy, galactic, humble, jazzy, kilted, lyrical]` (see [ROS 2 distro compatibility](#ros-2-distro-compatibility))

Supported platforms:
- Linux with glibc 2.31+ (Ubuntu 20.04+ or similar): x86_64, arm64, armhf, riscv64
- Linux with musl libc (Alpine): x86_64, arm64. Needs the C++ runtime: `apk add libstdc++`
- Windows (Windows 10+ x86_64, ARM64)
- macOS (macOS 14+ Intel, Apple Silicon)
- Android (Android 8.0+ / API 26: arm64-v8a, armeabi-v7a, x86_64; devices with 16 KB memory pages are supported)

Requires Java 17 or newer. Works on NVIDIA Jetson and on Raspberry Pi 2 or newer (ARMv7 and up, including the Zero 2)!

> [!NOTE]
> The native libraries bundled for macOS and Windows are older builds without DDS-Security. jros2 works with them,
> but SROS2 is not available on these two platforms yet. See [SROS2 (DDS-Security)](#sros2-dds-security).

## Features
- Fully compatible with ROS 2 foxy or newer
- Does not require a ROS 2 installation on the system
- Ready-to-use Java library, just add to your Maven or Gradle dependencies!
- Publish and subscribe to ROS 2 topics, with the same QoS defaults and presets as rclcpp
- Nodes appear in the ROS 2 graph (`ros2 node list`, `ros2 node info`, `ros2 topic info -v`), with namespaces and the
  ROS 2 naming rules
- RIHS01 type hashes, shown by `ros2 topic info -v` on Iron and newer
- UTF-8 strings and wide strings, byte-compatible with rclcpp and rclpy
- SROS2 / DDS-Security (Linux, Linux musl and Android)
- ROS 2 discovery settings: `ROS_DOMAIN_ID`, `ROS_LOCALHOST_ONLY`, `ROS_AUTOMATIC_DISCOVERY_RANGE`, `ROS_STATIC_PEERS`
- Supports custom message types
- Generate Java classes from ROS 2 .msg files
- Shared DDS participants (`ROS2Context`), callback executors and scheduling settings for the Fast-DDS threads
- Fast-DDS backend
- Minimal and fast implementation
- Fully thread-safe
- Async and allocation-free API: publishing, receiving and statistics allocate nothing in steady state, on the Java
  and the native heap, proven by zero-tolerance gates (see [Allocation-free hot paths](#allocation-free-hot-paths))
- (soon) ROS 2 services
- (soon) ROS 2 actions
- (soon) ROS 2 parameters

## Usage
Read in-depth documentation on the [Wiki](https://github.com/ihmcrobotics/jros2/wiki)!

The `develop` branch documents the upcoming 1.6.0 release. See [CHANGELOG.md](CHANGELOG.md) for what changed since
1.5.1, and [Migration from 1.5.x](#migration-from-15x) before upgrading.

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

See [Android notes](#android-notes) below and the [Android Support Wiki](https://github.com/ihmcrobotics/jros2/wiki/Android-Support) for complete setup instructions and examples.

### Here's the basics:

Create a ROS2Node:
```java
int domainId = 100;
ROS2Node node = new ROS2Node("my_node", domainId); // Without a domain ID, ROS_DOMAIN_ID is used (or 0)
// Call node.close() when you're done with the node!
```

Create a ROS2Topic:
```java
ROS2Topic<Int32> intTopic = new ROS2Topic<>("/int_topic", Int32.class);
```

Create a ROS2Publisher and publish a message on the intTopic:
```java
ROS2Publisher<Int32> intPublisher = node.createPublisher(intTopic);
Int32 message = new Int32();
message.setData(123);
intPublisher.publish(message);
```

Create a subscription to the intTopic:
```java
ROS2Subscription<Int32> subscription = node.createSubscription(intTopic, reader -> {
  Int32 message = reader.read();
  // Do something with the message!
});
```
`reader.read()` creates a new message. To stay allocation-free, keep one message and use `reader.read(message)`.

### Nodes, namespaces and contexts

Node names, namespaces and topic names follow the ROS 2 rules and are checked like rclcpp checks them. An invalid name
throws an `IllegalArgumentException` that gives the reason and the position. Topic names are checked when a publisher or
subscription is created, not when the `ROS2Topic` is built. Relative topic names are resolved against the node namespace,
`~` stands for the node's fully qualified name, and `{node}` and `{ns}` are substituted:
```java
ROS2Node node = new ROS2Node("talker", ROS2NodeOptions.defaults().namespace("/robot").domainId(42));
node.createPublisher(new ROS2Topic<>("chatter", std_msgs.String_.class));    // publishes on /robot/chatter
node.createPublisher(new ROS2Topic<>("~/status", std_msgs.String_.class));   // publishes on /robot/talker/status
```

`ROS2NodeOptions` also sets the transports, the callback executor, thread settings, the SROS2 enclave, the discovery
range and static peers. Options left unset fall back to the [configuration](#configuration).

Each node has its own DDS participant by default. Nodes created with a `ROS2Context` share one participant, like nodes in
one rclcpp context, which saves threads, sockets and discovery traffic:
```java
try (ROS2Context context = ROS2Context.create(ROS2NodeOptions.defaults().domainId(42)))
{
   ROS2Node talker = new ROS2Node("talker", ROS2NodeOptions.defaults().context(context));
   ROS2Node listener = new ROS2Node("listener", ROS2NodeOptions.defaults().namespace("/robot").context(context));
   // ...
} // closes both nodes, then the participant
```
The participant-level options (domain, transports, thread settings, enclave, discovery) come from the context. Within
one participant a topic name can only have one message type.

### Quality of service

`ROS2QoSProfile.DEFAULT`, and every `new ROS2QoSProfile()`, is RELIABLE, VOLATILE, KEEP_LAST(10), like rclcpp's
default, for publishers and for subscriptions. QoS is mapped to Fast-DDS the same way rmw_fastrtps maps it. The presets
match their rmw counterparts:

| Preset | History | Reliability | Durability |
|---|---|---|---|
| `DEFAULT`, `RELIABLE`, `SERVICES_DEFAULT` | KEEP_LAST 10 | RELIABLE | VOLATILE |
| `BEST_EFFORT` | KEEP_LAST 10 | BEST_EFFORT | VOLATILE |
| `SENSOR_DATA` | KEEP_LAST 5 | BEST_EFFORT | VOLATILE |
| `PARAMETERS`, `PARAMETER_EVENTS` | KEEP_LAST 1000 | RELIABLE | VOLATILE |
| `ROSOUT` | KEEP_LAST 1000 | RELIABLE | TRANSIENT_LOCAL, lifespan 10 s |
| `CLOCK` | KEEP_LAST 1 | BEST_EFFORT | VOLATILE |
| `SYSTEM_DEFAULT` | Fast-DDS defaults | Fast-DDS defaults | Fast-DDS defaults |

The presets cannot be changed: their setters throw `UnsupportedOperationException`. Copy a preset to change it:
```java
ROS2QoSProfile qos = new ROS2QoSProfile(ROS2QoSProfile.SENSOR_DATA);
qos.depth(1);
ROS2Subscription<Image> images = node.createSubscription(imageTopic, reader -> { /* ... */ }, qos);
```
A RELIABLE subscription does not receive from BEST_EFFORT publishers. Most camera and sensor drivers publish BEST_EFFORT,
so subscribe to them with `SENSOR_DATA` or `BEST_EFFORT`.

### Callbacks and threads

- A subscription with a callback takes every sample from Fast-DDS and calls the callback once per sample. Inside the
  callback, `read()` reads that sample. A sample that the callback does not read is still consumed, as in rclcpp. A
  subscription without a callback is polled with `read()` and `readLatest()`.
- Data callbacks run on the Fast-DDS receive thread by default. With `ROS2NodeOptions.callbackExecutor(executor)` they
  run on your `Executor` instead, one at a time per subscription. Matched callbacks are not run on the executor.
- From a callback you may publish, destroy publishers and subscriptions, and close nodes and contexts. Closing from a
  callback finishes after the callback returns. Creating publishers or subscriptions on the participant whose callback is
  running throws an `IllegalStateException`, because Fast-DDS would deadlock. Use a callback executor if a data callback
  must create endpoints. Details are in [Migration from 1.5.x](#migration-from-15x).
- `ROS2ThreadSettings` sets the scheduling policy, priority, CPU affinity and stack size of the native Fast-DDS threads of
  a participant, for all threads or per kind of thread:
  ```java
  ROS2ThreadSettings threads = new ROS2ThreadSettings().affinityMask(0b1100)                 // CPUs 2 and 3
        .forThreads(ROS2ThreadSettings.Kind.RECEPTION, new ROS2ThreadSettings().schedulingPolicy(1).priority(30)); // SCHED_FIFO 30
  ROS2Node node = new ROS2Node("robot", ROS2NodeOptions.defaults().threadSettings(threads));
  ```
  A stack size must be -1 (the default) or a multiple of 16384 bytes that is at least 131072 bytes. Any other value throws
  an `IllegalArgumentException`, because some operating systems would abort the process. When reception thread settings
  are used with the builtin transports, the `FASTDDS_BUILTIN_TRANSPORTS` environment variable no longer applies to that
  participant.
- `AsyncROS2Node` creates `AsyncROS2Publisher`s. Their `publish()` copies the message into a preallocated queue and
  returns; the node's publish thread writes it. When the queue is full the message is dropped and counted
  (`getDroppedMessageCount()`); `offer()` returns false in that case. Pass a `ThreadFactory` to the `AsyncROS2Node`
  constructor to set real-time scheduling for the publish thread.
- Message statistics (size, period, age) are off by default. Turn them on per publisher or subscription with
  `setStatisticsEnabled(true)`. Recording and reading them is allocation-free.
- `ROS2CallbackExecutor` is an allocation-free `Executor` for callbacks (see
  [Allocation-free hot paths](#allocation-free-hot-paths)).

### Allocation-free hot paths

jros2 is used in real-time control loops, where a garbage collection pause or a contended `malloc` is a failure. After
setup and warm-up, everything a control loop does every tick allocates nothing, neither on the Java heap nor on the
native heap, on every thread that does work for it (the publishing thread, the async publish thread, the callback
executor's workers, the Fast-DDS threads that run callbacks or move the data):

- `publish()` on a `ROS2Publisher` (also from several threads at once) and on an `AsyncROS2Publisher` (the caller and
  the node's publish thread), RELIABLE and BEST_EFFORT, KEEP_LAST, small and fragmented large messages (for example 1 to
  4 MB images), with intraprocess delivery off or on, with the zero-copy native functions and with the copying fallback
  (`-Djros2.native.forceAbiV1=true`);
- receiving: callbacks on the Fast-DDS thread or on a `ROS2CallbackExecutor`, `reader.read(message)`,
  `read(message, sampleInfo)`, `readLatest(message)`, polling with `read(message)` / `readLatest(message)`,
  `getUnreadMessageCount()` and the other per-tick queries (matched counts, GUIDs, malformed sample count);
- serialization, deserialization and `set()` of every bundled message type (and of every field kind the generator
  supports) at stable sizes, including strings, nested messages, bounded and unbounded sequences and arrays;
- message statistics (`setStatisticsEnabled(true)`): recording on every publish and receive, and reading them with
  `getMessageSize(...)`, `getMessagePeriod(...)`, `getMessageAge(...)` and `readMessageSizeStatistics(statistics)` /
  `readMessagePeriodStatistics(statistics)` / `readMessageAgeStatistics(statistics)` into a `Statistics` you keep.
  Generated messages with a `std_msgs/Header header` field implement `ROS2Message.WithHeader`, so the age statistic
  reads the header without reflection; other message classes with a `Header` getter are read through reflection,
  which is linked when statistics are enabled.

What allocates, by design, outside the hot path: creating and closing contexts, nodes, publishers and subscriptions;
the first messages of a path (warm-up: class loading, JIT compilation, buffers sized for the first message); a message,
sequence, string or buffer growing beyond its previous high-water mark; `toString()`, `equals()` and `hashCode()` of
messages; and error paths that throw or log.

Use these allocation-free variants in a control loop:

- `reader.read(message)` and `subscription.readLatest(message)` with a message you keep. `read()` and `readLatest()`
  without an argument create a new message.
- String fields: `getName()` returns the field's `StringBuilder` (read it as a `CharSequence`), and `setName(String)` /
  `setName(CharSequence)` copy into it. `getNameAsString()` creates a `String`, as do `IDLStringSequence.getAsString(i)`
  and `toStringArray()`. `CDRBuffer.newStringBuilder(capacity)` creates a builder that stays allocation-free when
  non-Latin-1 text arrives.
- Callback executors: `ROS2NodeOptions.callbackExecutor(new ROS2CallbackExecutor("callbacks"))` runs the callbacks on a
  worker thread without allocating (pass a `ThreadFactory` for real-time scheduling). The JDK's executors
  (`ThreadPoolExecutor`, `Executors.newSingleThreadExecutor()`, ...) allocate a queue node for every task; jros2's own
  dispatch to them is allocation-free.
- `AsyncROS2Publisher.publish()` copies into a preallocated queue slot; a full queue drops the message without
  allocating (`getDroppedMessageCount()`).

The Fast-DDS 3.6.2 in the bundled native libraries carries jros2's patches in
[src/native/fastdds-patches](src/native/fastdds-patches), applied during the native build (`scripts/docker/deps.sh`,
`scripts/cppbuild.bash`). They remove Fast-DDS's own heap allocations from the steady-state paths: the keyless reader
instance that was re-inserted for every sample, the shared-memory buffer handles allocated for every message and fragment,
the shared-memory watchdog's per-second path strings, the exceptions thrown when the shared-memory segment or a port queue
is full, and payload pool nodes and send buffers that grew at unpredictable times (jros2's participant profiles
preallocate 80 network buffers per send buffer; a message is sent at 62). The macOS and Windows libraries get the same patches
when the **Build natives** workflow rebuilds them; the ones bundled today predate the patches, so on macOS and Windows
Fast-DDS itself still allocates on these paths (jros2's Java and JNI code does not).

**Large BEST_EFFORT messages between processes on one host.** Fast-DDS's builtin shared-memory transport has a 512 KiB
segment per participant, as with rmw_fastrtps. A BEST_EFFORT sample larger than that (for example a camera image) does
not fit the segment, a fragment that cannot be placed is dropped, and BEST_EFFORT never repairs it, so such samples are
often lost. RELIABLE samples are repaired and arrive. jros2 keeps Fast-DDS's default; for large BEST_EFFORT data on one
host, give the publishing and subscribing participants a shared-memory transport with a larger segment:

```java
TransportDescriptorType largeShm = TransportDescriptorTypeTools.copyOf(TransportDescriptorTypeTools.createSHMDescriptor(), "large_shm");
largeShm.setSegmentSize(8L * 1024 * 1024);   // bytes; holds a burst of a few large samples
largeShm.setPortQueueCapacity(8192L);        // queued buffers per receiving port
ROS2Node camera = new ROS2Node("camera", ROS2NodeOptions.defaults().transports(largeShm, TransportDescriptorTypeTools.createUDPv4Transport()));
```

(`TransportDescriptorTypeTools` is in `us.ihmc.fastddsjava.profiles`, `TransportDescriptorType` in
`us.ihmc.fastddsjava.profiles.gen`.) Intraprocess delivery (`FASTDDS_INTRAPROCESS_DELIVERY=true`) avoids the transport
for endpoints in the same process.

The gates that prove all of this run with `./gradlew :jros2-test:allocationTest` (see [Tests and coverage](#tests-and-coverage)).

### Configuration

Each setting can be given as a Java system property, an environment variable, or a key in a `jros2.properties` file, in
that order of priority. jros2 uses the first `jros2.properties` it finds: in the working directory, on the classpath, or
`~/.ihmc/jros2.properties` (which it creates with the default domain, intraprocess and whitelist values if no file exists).

| Setting | Environment variable | System property | `jros2.properties` key |
|---|---|---|---|
| Domain ID (default 0) | `ROS_DOMAIN_ID` | `ros.domain.id` | `jros2.ros.domain.id` |
| Intraprocess delivery (default off) | `FASTDDS_INTRAPROCESS_DELIVERY` | `fastdds.intraprocess.delivery` | `jros2.fastdds.intraprocess.delivery` |
| Interface whitelist (comma separated addresses or interface names) | `FASTDDS_INTERFACE_WHITELIST` | `fastdds.interface.whitelist` | `jros2.fastdds.interface.whitelist` |
| ROS 2 distro of the peers | `ROS_DISTRO` | `ros.distro` | `jros2.ros.distro` |
| ROS graph format: `AUTO` (default), `IRON_PLUS`, `HUMBLE`, `DUAL` | `JROS2_GRAPH_COMPATIBILITY` | `jros2.graph.compatibility` | `jros2.graph.compatibility` |
| Localhost only (deprecated in ROS 2; `1` enables it) | `ROS_LOCALHOST_ONLY` | `ros.localhost.only` | `jros2.ros.localhost.only` |
| Discovery range: `OFF`, `LOCALHOST`, `SUBNET` (default), `SYSTEM_DEFAULT` | `ROS_AUTOMATIC_DISCOVERY_RANGE` | `ros.automatic.discovery.range` | `jros2.ros.automatic.discovery.range` |
| Static peers (`;` separated host names or IPv4 addresses) | `ROS_STATIC_PEERS` | `ros.static.peers` | `jros2.ros.static.peers` |
| SROS2 | `ROS_SECURITY_ENABLE`, `ROS_SECURITY_STRATEGY`, `ROS_SECURITY_KEYSTORE`, `ROS_SECURITY_ENCLAVE_OVERRIDE`, `ROS_SECURITY_LOG_FILE`, `ROS_SECURITY_LOG_PUBLISH`, `ROS_SECURITY_LOG_VERBOSITY` | `ros.security.enable`, `ros.security.strategy`, ... (lower case, dots) | `jros2.ros.security.enable`, ... |
| SROS2 legacy algorithm names: `AUTO` (default), `true`, `false` | `JROS2_SECURITY_LEGACY_ALGORITHMS` | `jros2.security.legacy.algorithms` | `jros2.security.legacy.algorithms` |

The discovery settings follow rcl's rules: `ROS_LOCALHOST_ONLY=1` forces LOCALHOST and drops the static peers, an invalid
`ROS_AUTOMATIC_DISCOVERY_RANGE` falls back to LOCALHOST with a warning, and static peers are ignored with OFF and
SYSTEM_DEFAULT. Static peers given as host names are resolved when the node is created; a name that cannot be resolved
throws an `IllegalArgumentException`. `ROS2NodeOptions.discoveryRange(...)` and `staticPeers(...)` override the settings.

Intraprocess delivery applies to the whole process and is read once, when jros2 loads. It is also on by default when the
`GITHUB_ACTIONS` environment variable is set. The old misspelled variable `FASTDDS_INRAPROCESS_DELIVERY` is still read
when `FASTDDS_INTRAPROCESS_DELIVERY` is not set.

Set the system property `jros2.node.printout=false` to turn off the configuration summary that is logged for every new
participant.

### ROS 2 distro compatibility

The full test suite passes inside the `osrf/ros:<distro>-desktop` containers of Foxy, Galactic, Humble, Jazzy, Kilted
and Lyrical, with intraprocess delivery on and off. In every one of these distros the ROS 2 tools and rclpy/rclcpp nodes
were checked against jros2: the graph (`ros2 node list`, `ros2 node info`, `ros2 topic info -v`), non-ASCII strings in
both directions, `std_msgs/Empty`, 2 MiB images, and QoS matching. CI runs the suite on Foxy, Humble, Jazzy, Kilted and
Lyrical; Galactic is not in CI. Iron and Rolling were not tested.

| Distro | Middleware of the peers | Notes |
|---|---|---|
| Foxy | Fast-DDS 2.1 | Set `ROS_DISTRO=foxy`. No SROS2 interop. No type hashes in the CLI. |
| Galactic | Cyclone DDS (default) | Set `ROS_DISTRO=galactic`. No SROS2 interop. No type hashes in the CLI. |
| Humble | Fast-DDS 2.6 | SROS2 needs the legacy algorithm names (automatic with `ROS_DISTRO=humble`). No type hashes in the CLI. |
| Jazzy | Fast-DDS 2.14 | |
| Kilted | Fast-DDS 3.2 | |
| Lyrical | Fast-DDS 3.6 | |

- **ROS graph format.** Humble and older use 24-byte GIDs in `ros_discovery_info`, Iron and newer 16-byte GIDs.
  `JROS2_GRAPH_COMPATIBILITY=AUTO` (the default) picks `HUMBLE` when `ROS_DISTRO` is foxy, galactic or humble,
  `IRON_PLUS` when it is iron, jazzy, kilted, lyrical or rolling, and `DUAL` (both formats) when it is unset or unknown.
  Humble and Iron+ tools understand `DUAL`; Humble prints harmless "sequence size exceeds remaining buffer" warnings.
- **Foxy and Galactic: set `ROS_DISTRO`** (or `JROS2_GRAPH_COMPATIBILITY=HUMBLE`). A 16-byte GID sample (`DUAL` or
  `IRON_PLUS`) makes their Fast-DDS based processes abort with `std::bad_alloc`, and Galactic's Cyclone DDS lists the node
  twice. Native Iron and newer nodes have the same effect on them, so do not mix Foxy or Galactic with Iron+ nodes in one
  domain.
- **SROS2.** Secure communication works with Humble, Jazzy, Kilted and Lyrical. Humble's Fast-DDS only accepts the legacy
  certificate algorithm names; jros2 sends them when `ROS_DISTRO` is foxy, galactic or humble, or when
  `JROS2_SECURITY_LEGACY_ALGORITHMS=true`. Jazzy and Kilted were checked to accept the legacy names too. No secure session
  forms with Foxy or Galactic (native Humble nodes cannot talk securely to Foxy either).
- **Large best-effort messages.** 2 MiB BEST_EFFORT samples were lost over shared memory with Fast-DDS 3.x peers
  (Kilted, Lyrical; native nodes of those distros lose them too), and mostly lost over UDP with Foxy, Galactic and Humble
  peers. RELIABLE delivered every 2 MiB sample with every distro, so use RELIABLE for large messages.
- **Lyrical demo nodes.** The `demo_nodes_cpp` talker and listener of Lyrical use `example_interfaces/msg/String` on
  `/chatter`; those of the older distros use `std_msgs/msg/String`.
- **Galactic with Cyclone DDS** needs about 1.1 s to match a new publisher with a jros2 subscription. Tools that publish
  without waiting for a match, such as `ros2 topic pub --times` on Foxy and Galactic, lose the first samples.

### SROS2 (DDS-Security)

jros2 reads the SROS2 settings the way rcl does and configures Fast-DDS like rmw_fastrtps:
```bash
export ROS_SECURITY_ENABLE=true
export ROS_SECURITY_STRATEGY=Enforce
export ROS_SECURITY_KEYSTORE=~/sros2_keystore   # made with: ros2 security create_keystore / create_enclave
```
```java
ROS2Node talker = new ROS2Node("talker", ROS2NodeOptions.defaults().enclave("/talker_listener/talker"));
```
- The enclave defaults to `/`. `ROS_SECURITY_ENCLAVE_OVERRIDE` replaces it for the keystore lookup.
- With `Enforce`, creating a node throws an `IllegalStateException` when the keystore or a required file is missing, or
  when the loaded native libraries have no DDS-Security. With `Permissive` jros2 logs a warning and runs unsecured.
- The native libraries for Linux (x86_64, arm64, armhf, riscv64), Linux musl and Android include DDS-Security. The
  libraries bundled for macOS and Windows do not, so `Enforce` fails there and `Permissive` runs unsecured.
- Keystore permissions are per domain and must grant the ROS topic names (`rt/...`) and `ros_discovery_info`, as for any
  ROS 2 node.

### Native libraries

The jar contains the Fast-DDS libraries for every desktop platform under `fastddsjava/native/<platform>/`. At startup
jros2 copies the libraries of the current platform to `<java.io.tmpdir>/jros2-natives-<user>/<platform>-<hash>/` and loads
them from there. `<hash>` comes from the library contents, so later runs reuse the directory. If libraries cannot be loaded
from the temporary directory (for example a `noexec` mount), jros2 uses `~/.cache/jros2` instead.

| Option | Environment variable | System property |
|---|---|---|
| Load the libraries from this directory, without copying | `JROS2_NATIVE_PATH` | `jros2.native.path` |
| Base directory for the copies | `JROS2_NATIVE_EXTRACT_DIR` | `jros2.native.extract.dir` |
| Platform directory to use instead of the detected one, e.g. `linux-musl-arm64` | | `jros2.native.platform` |

The system property wins over the environment variable. On Linux, jros2 detects musl libc (Alpine) by itself. The musl
libraries need the system's `libstdc++` and `libgcc` (`apk add libstdc++`). jros2 also works with older native
libraries that lack the newer zero-copy functions (currently the macOS and Windows builds) by using copying code paths
instead; `-Djros2.native.forceAbiV1=true` selects those paths with any native library.

The libraries are built with the scripts in [scripts/docker](scripts/docker/README.md) and `scripts/cppbuild.bash`, from
Fast-DDS 3.6.2 with jros2's patches ([src/native/fastdds-patches](src/native/fastdds-patches), applied in name order with
exact context; see [Allocation-free hot paths](#allocation-free-hot-paths)).

### Android notes

- jros2-android needs Android 8.0 (API 26) or newer. It was tested on emulators with API 26, 31, 35 (16 KB page size
  image) and 36.1, with the x86_64 libraries and, through the emulator's ARM translation, the arm64-v8a libraries. The
  armeabi-v7a libraries were only checked statically.
- The 64-bit libraries (arm64-v8a, x86_64) are aligned for 16 KB memory pages.
- Wi-Fi drivers often drop the multicast packets that DDS discovery uses. Hold a multicast lock while nodes should be
  discoverable. The AAR's manifest already declares the `CHANGE_WIFI_MULTICAST_STATE` permission (and `INTERNET` and
  `ACCESS_NETWORK_STATE`):
  ```java
  try (ROS2Android.MulticastLockHandle multicast = ROS2Android.acquireMulticastLock(context);
       ROS2Node node = new ROS2Node("phone_node"))
  {
     // ...
  }
  ```
- The AAR ships R8/ProGuard consumer rules, so minified apps need no extra rules for jros2.
- Without your own transports or an interface whitelist, jros2 uses only UDPv4 on Android (no shared memory transport).
- Static peers given as host names are resolved when the node is created. Create such nodes off the main thread, or use
  IPv4 addresses.
- SROS2 works on the device. Copy the keystore into app storage and set the settings as system properties before you
  create nodes, e.g. `System.setProperty("ros.security.keystore", keystoreDir.getAbsolutePath())`.

### Generating message classes

The Gradle plugin `us.ihmc.jros2.generator` generates Java classes from the `.msg` files of ROS 2 packages with a
`jros2GenTask`. See [examples/custom-message-class](examples/custom-message-class). Generated classes carry the RIHS01
type hash of the message (`typeHash`). When your messages reference types that jros2 does not bundle, list the packages
that define them in `dependencyPaths` (for example `/opt/ros/jazzy/share`), so their hashes can be computed. The parser
follows `rosidl_adapter` and fails generation with a clear message on lines that ROS 2 rejects.

Classes generated with plugin 1.5.1 still work with the new library, but have no type hash, still send an empty payload for
messages without fields, and copy nested message sequences by reference in `set()`. Regenerate them with the new plugin.

### Migration from 1.5.x

- **Subscriptions are RELIABLE by default.** `ROS2QoSProfile.DEFAULT` and `new ROS2QoSProfile()` are now RELIABLE,
  VOLATILE, KEEP_LAST(10) for subscriptions too (1.5.x subscriptions were BEST_EFFORT). A default subscription no longer
  receives from BEST_EFFORT publishers: use `ROS2QoSProfile.SENSOR_DATA` or `BEST_EFFORT` for those.
- **QoS presets are immutable.** Changing `DEFAULT`, `RELIABLE` or `BEST_EFFORT` throws `UnsupportedOperationException`;
  derive a copy with `new ROS2QoSProfile(preset)`.
- **`SYSTEM_DEFAULT` means the Fast-DDS default,** as in rmw. A publisher whose durability is `SYSTEM_DEFAULT` is now
  TRANSIENT_LOCAL, the Fast-DDS DataWriter default; 1.5.x made it VOLATILE.
- **Names are validated like rmw.** Node names, namespaces and topic names that rmw rejects (for example `my-node`,
  `my node` or `/my-topic`) throw an `IllegalArgumentException`. The node name is now the ROS node name shown in the
  graph. Relative topic names are resolved against the node namespace (1.5.x only warned and used them as they were).
- **Creating endpoints from callbacks.** Creating a publisher or subscription from inside a subscription callback (data or
  matched callback) that runs on a Fast-DDS thread:
  - on the participant of that subscription (the same node, or any node of the same `ROS2Context`) throws an
    `IllegalStateException` right away, because Fast-DDS deadlocks in that case;
  - on another participant is allowed. jros2 creates DDS entities one at a time per process, though. If another thread
    holds that creation lock while it creates an endpoint on the callback's participant for 500 ms, or the lock stays busy
    for 5 s, the call from the callback throws an `IllegalStateException` instead of deadlocking.

  Data callbacks that run on a `callbackExecutor`'s own threads have neither limit; matched callbacks are not run on the
  executor.
- **`close()` from a callback is deferred.** Closing a node or a `ROS2Context`, or destroying a publisher or subscription,
  from inside a callback marks it closed at once; the native teardown runs on the `jros2-deferred-teardown` thread after
  the callback returns. From other threads `close()` is still synchronous.
- **Callback subscriptions consume every sample.** The callback runs once per sample, and samples it does not read are
  not left for polling. `getUnreadMessageCount()` is the Fast-DDS unread count, which stays near 0 in callback mode.
  Samples and matches that arrived before the subscription was set up are delivered from `createSubscription`, possibly
  on the calling thread.
- **Statistics are opt-in.** Call `setStatisticsEnabled(true)` on a publisher or subscription; 1.5.x recorded subscription
  statistics always.
- **Messages without fields send 1 byte.** `std_msgs/Empty` and constant-only messages now carry the 1-byte member that
  rclcpp and rclpy send and expect. 1.5.x sent an empty payload, which rclcpp and rclpy dropped. jros2 still accepts the
  empty payload.
- **Strings are UTF-8.** 1.5.x cut every character to one byte. A string that contains a NUL character cannot be
  serialized any more: `ROS2Publisher.publish()` throws an `IllegalArgumentException`, and an `AsyncROS2Publisher` logs
  it and counts the message as dropped. Invalid UTF-8 that is received is replaced by U+FFFD, so non-ASCII text from
  1.5.x senders arrives as U+FFFD. Wide strings are UTF-16 code units, as rmw_fastrtps sends them.
- **Sequence bounds are enforced.** Adding to a bounded sequence beyond its bound (`add`, `addAll`, `putAt`, `set`) or
  serializing such a sequence throws an `IllegalArgumentException`, and `ensureMinCapacity` returns false instead of
  stopping at the bound. A received sample that is malformed or over its bounds is dropped and counted by
  `ROS2Subscription.getMalformedSampleCount()`.
- **Async publishers.** A full queue still drops the message, but now counts it (`getDroppedMessageCount()`), and
  `offer()` returns false. `AsyncROS2Node.close()` first writes the queued messages, for up to 2 s.
- **Stricter `.msg` parser.** Lines that 1.5.x silently ignored now fail generation: for example constant arrays, bounded
  string constants, default values for nested types, `time`/`duration` fields, duplicate names and out-of-range defaults.
- **Deprecated.** `ROS2Node.createService`, `declareParameter` and `getParameter` are deprecated and throw
  `UnsupportedOperationException` (1.5.x threw `RuntimeException("Not yet implemented")`). The protected
  `ROS2Publisher.closeLock` is deprecated and no longer used.
- **One message type per topic and participant.** Using one topic name with two message types in one node (or one
  `ROS2Context`) throws an `IllegalArgumentException`.
- **Graph traffic.** Every participant now has one more DataWriter for the ROS graph. Because of it, a subscription can see
  a new publisher about 100 ms after the publisher saw the subscription. A publisher that sends to a BEST_EFFORT
  subscription right after `waitForSubscription()` can lose the first samples.
- **Native library files.** The Linux libraries are now named by their SONAME (`libfastcdr.so.2`, `libfastdds.so.3.6`) and
  are copied to a cache that is reused across runs (see [Native libraries](#native-libraries)) instead of a new
  temporary directory per run.

No public class or method was renamed or removed.

### Tests and coverage

The tests are in `src/test/java` (Gradle project `:jros2-test`, JUnit 5). Tests that create DDS participants use
`ROS_DOMAIN_ID`; set it to keep test runs on one machine apart. Tests that call the `ros2` CLI run only on Linux when
`/opt/ros/$ROS_DISTRO/setup.bash` exists.

```bash
./gradlew :jros2-test:test                            # unit and integration tests, records JaCoCo coverage
./gradlew :jros2-test:jacocoTestReport                # HTML and XML report: src/test/build/reports/jacoco/test/
./gradlew :jros2-test:jacocoTestCoverageVerification  # fails if the coverage of the hand-written code drops below the thresholds
./gradlew :jros2-test:allocationTest                  # allocation gates, see below
```

`:jros2-test:allocationTest` runs every test tagged `@Tag("allocation")`, each class in its own JVM, in four tasks: with
the java-allocation-instrumenter agent and with `-XX:-DoEscapeAnalysis`, each with intraprocess delivery off and on
(`allocationTest`, `allocationTestNoEA`, `allocationTestIntraprocess`, `allocationTestIntraprocessNoEA`; the first runs
the other three). The gates have no tolerance: one allocated object or one `malloc` fails them.

- Java heap, every thread (`AllocationGate`): the agent reports every allocation made by bytecode, independently of
  escape analysis; HotSpot's per-thread allocated-bytes counters of all threads also see objects created by JNI code and
  by the JVM; a JFR pass reports monitor inflation (contended `synchronized`). Covered: publish, async publish, concurrent
  publishers, callbacks (inline, `ROS2CallbackExecutor`), polling, `SampleInfo`, statistics, large fragmented messages,
  several nodes and topics, idle background activity, a paced 1 kHz loop. Static checks keep string literals out of the
  hot-path classes (HotSpot creates them on the thread that triggers a class's first C2 compilation).
- Messages: serialization, deserialization and `set()` of every bundled type and of every generated field kind, with long
  windows that cross every JIT threshold, and in child JVMs without the agent (G1, ParallelGC, no class data sharing,
  C1 only, C2 only, escape analysis off).
- Native heap, every hot-path thread (Linux/glibc, needs a C compiler: an `LD_PRELOAD` shim counts `malloc`, `new`,
  `free`, `mmap` with their stacks): every native method, the publish/receive matrix of the transports and ABIs, and
  Fast-DDS's background threads. A call made by the JNI layer, Fast-DDS or Fast-CDR (also through libstdc++ or a JNI
  function), by a JVM or JDK native method that Java code called, or by HotSpot monitor inflation fails the gate in any
  attempt; a window is measured again only when Fast-DDS processed discovery data in it. Purely JVM-internal calls (JIT
  deoptimization and on-stack replacement, handshakes, garbage collector buffers) are reported separately.
- `ControlLoopAllocationTest`: 10 s of a robot-like process (three nodes in a `ROS2Context` and one more node, 1 kHz
  `JointState`, a 1 MB image through an `AsyncROS2Publisher`, inline and executor callbacks, polling, statistics) with the
  Java heap gate on all threads and the native heap counter on the hot-path threads in the same windows.

Every gate was shown to fail when an allocation is injected. The reports are written to
`src/test/build/allocation-gate/<task>/`; `-Pjros2.allocation.diagnose` names the source of allocations the agent cannot
see, and `-Pjros2.allocation.jit=default|c1|c2|interpreter` runs the gates under another JIT configuration.

The report covers the jros2 runtime (`us.ihmc.jros2`, `us.ihmc.fastddsjava`), the bundled message classes and the message
parser and generator, including code run by the child JVMs that some tests start. The thresholds (90 % of the lines and
82 % of the branches overall, 65 % of the lines per file) apply to the hand-written code only: the bundled interfaces
(`src/main/java-interfaces`) and the classes generated from the Fast-DDS XML schema (`us.ihmc.fastddsjava.profiles.gen`) are
excluded. The native layer (`src/native`) is not measured. Gradle does not rerun the tests when only an environment
variable changes, so use `--rerun` for a second run with, for example, `FASTDDS_INTRAPROCESS_DELIVERY=true`.

### Talker and listener example
Run a talker and listener example with `./scripts/run_talker_listener.sh`. If you have a local ROS 2 installation or use a ROS 2 container, you
can verify that ROS 2 is able to communicate with jros2. The example publishes `example_interfaces/msg/String` on
`/chatter`.

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

More examples are in [examples](examples): shared memory transport, an OpenCV webcam publisher, custom message classes,
and a native library smoke test.
