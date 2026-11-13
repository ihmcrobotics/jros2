# Changelog

## Unreleased (1.6.0)

Read [Migration from 1.5.x](README.md#migration-from-15x) before upgrading: several defaults changed to match ROS 2.

### Fixed
- With intraprocess delivery on, creating a publisher or subscription from a subscription callback could deadlock with
  discovery. jros2 now uses Fast-DDS's `USER_DATA_ONLY` mode instead of `FULL`: subscription callbacks still run inside
  `publish()`; discovery data within the process goes through the transports, as between processes. This mode needs
  Fast-DDS patches 0008 and 0009 (see Added).
- With intraprocess delivery on, deleting publishers and subscriptions of one process at the same time could crash the JVM
  (seen on Linux, macOS and Windows): a reader called a writer of the same process that another thread was deleting.
  Fast-DDS patch 0008 keeps the writer alive during the call, and patch 0009 closes the same kind of race on readers.
- A subscription could deliver the same message to its callback over and over (about two million times a second) and,
  with intraprocess delivery on, block every publisher of the process on that topic until the callback had run 2^31
  times; `readLatest()` could loop the same way. Fast-DDS 3.6.2 sometimes cannot remove a sample that it returned from the
  reader history (when a sample of another publisher with exactly the same source timestamp precedes it, which the 1 us
  clock of macOS and the 100 ns clock of Windows make likely), and then returns that sample on every later take. Seen
  on Windows with several publishers of one process. jros2 now skips a sample that was taken before and stops taking
  until the next notification when it gets one again, and Fast-DDS patch 0010 fixes the lookup.
- Concurrent `publish()` calls on one `ROS2Publisher` could crash the JVM. Serialization and the native write now run in
  one critical section.
- Strings are UTF-8 on the wire. 1.5.x cut every character to one byte, so non-ASCII text was garbled for rclcpp and
  rclpy. Wide strings are written as UTF-16 code units like rmw_fastrtps; characters outside the Basic Multilingual Plane
  were written incorrectly before.
- `std_msgs/Empty` and other messages without fields were dropped by rclcpp and rclpy. They now carry the 1-byte member
  that ROS 2 expects.
- Received samples are validated: every length is checked against the payload before anything is allocated, and a
  truncated, malformed or over-bound sample is dropped, logged and counted (`ROS2Subscription.getMalformedSampleCount()`).
- `AsyncROS2Publisher` could publish torn messages: the generated `set()` and copy constructors shared the elements of
  sequences of nested messages (for example `PoseArray`, `MarkerArray`, `TFMessage`), and a queue slot could be published
  before the copy into it had finished. Generated copies are now deep, and the queue publishes a slot only after the copy.
- `AsyncROS2Publisher` allocated on each of its first publishes, once per queue slot (64 by default), and more now that
  copies of nested messages are deep. The first publish now fills all the slots, so the publishes after it allocate nothing
  (see Changed).
- Closing a node no longer sleeps 50 ms while holding global locks.
- Closing nodes, contexts, publishers or subscriptions from inside a subscription callback, and creating endpoints from
  callbacks, no longer deadlock (see Changed).
- Native callback threads are detached from the JVM when they end (they leaked with every deleted participant), and
  `System.exit()` with live participants no longer hangs. Both need the rebuilt native libraries; Windows keeps its
  callback threads attached.
- A deleted listener can no longer call into Java (rebuilt native libraries). jros2 now checks every native handle and return
  code: when Fast-DDS cannot create a participant, topic or endpoint, it throws an `IllegalStateException` instead of
  continuing with a null handle.
- jros2-android called Java APIs that are newer than its minimum API 26 and fail with `NoSuchMethodError` on older
  devices: `ByteBuffer.put(int, ByteBuffer, int, int)` (API 35, used whenever a sequence grew), `String.formatted`
  (API 34) and `Thread.onSpinWait` (API 33). Its native libraries did not load on devices with 16 KB memory pages. All
  code now runs from API 26, an Android Lint gate fails the build on any newer API, and the 64-bit libraries are 16 KB
  aligned.
- The interface whitelist was checked against the first transport only; every transport is checked now.
- Message statistics reported a wrong maximum when every recorded value was negative.
- Generated Java no longer fails to compile when a `.msg` comment contains a backslash followed by `u` (such as
  `C:\users`).
- `.msg` default values are parsed like `rosidl_adapter` parses them (for example tab separators, escaped quotes and
  case-insensitive booleans), range-checked and written as correct Java literals, and sequence defaults are applied.
- The parser no longer crashes on a message definition that ends with a dependency delimiter line.
- jros2 nodes keep jros2's intraprocess delivery mode when other code in the process changes the process-wide default of
  `ProfilesXML.setIntraprocessDelivery` (Fast-DDS applies the library settings of every profile it loads to the whole
  process; every profile jros2 loads now states jros2's mode). New `ProfilesXML.getIntraprocessDelivery()`.
- Large messages between nodes on one host were delayed or lost: a message larger than the 512 KiB shared-memory segment
  of Fast-DDS's builtin transports (a 1 MB camera image) lost the fragments that did not fit, so RELIABLE samples arrived
  only after Fast-DDS's repair (about 11 ms for 1 MB, 23 ms and more for 2.7 MB, instead of 0.6 and 1.6 ms) and
  BEST_EFFORT samples were lost. Participants now use a 4 MiB segment (`ROS2NodeOptions.shmSegmentSize`,
  `JROS2_SHM_SEGMENT_SIZE`; see Changed).
- The environment variable `FASTDDS_INTRAPROCESS_DELIVERY` is now read under its correct name. The misspelled
  `FASTDDS_INRAPROCESS_DELIVERY` of 1.5.x is still read when the correct one is not set.

### Added
- Nodes appear in the ROS 2 graph: each participant publishes `ros_discovery_info`, so `ros2 node list`,
  `ros2 node info` and `ros2 topic info -v` show jros2 nodes and their endpoints. Humble (24-byte GID) and Iron+ (16-byte
  GID) formats are selected with `JROS2_GRAPH_COMPATIBILITY` / `jros2.graph.compatibility` (`AUTO`, `IRON_PLUS`, `HUMBLE`,
  `DUAL`); `AUTO` follows `ROS_DISTRO`. Participants announce their SROS2 enclave like rmw.
- RIHS01 type hashes: generated classes have a `typeHash` field, and endpoints advertise it like rmw (shown by
  `ros2 topic info -v` on Iron and newer).
- Node namespaces and ROS 2 name handling: `ROS2NodeOptions.namespace(...)`, relative and private (`~`) topic names,
  `{node}`/`{ns}`/`{namespace}` substitutions, `ROS2Node.getNamespace()`, `getFullyQualifiedName()` and
  `resolveTopicName(...)`.
- `ROS2NodeOptions` for the namespace, domain ID, transports, callback executor, thread settings, SROS2 enclave, discovery
  range, static peers and context, with a new `ROS2Node(String, ROS2NodeOptions)` constructor.
- `ROS2Context`: several nodes share one DDS participant, like nodes in one rclcpp context.
- Callback executors (`ROS2NodeOptions.callbackExecutor`): subscription data callbacks run on your `Executor` instead of
  the Fast-DDS receive thread.
- `ROS2ThreadSettings`: scheduling policy, priority, CPU affinity and stack size of the native Fast-DDS threads, for all
  threads or per kind of thread.
- SROS2 / DDS-Security: `ROS_SECURITY_ENABLE`, `ROS_SECURITY_STRATEGY`, `ROS_SECURITY_KEYSTORE`,
  `ROS_SECURITY_ENCLAVE_OVERRIDE`, `ROS_SECURITY_LOG_FILE`, `ROS_SECURITY_LOG_PUBLISH` and `ROS_SECURITY_LOG_VERBOSITY`,
  resolved like rcl. The native libraries for Linux, Linux musl and Android include DDS-Security.
- `JROS2_SECURITY_LEGACY_ALGORITHMS` / `jros2.security.legacy.algorithms` (`AUTO`, `true`, `false`): announce the legacy
  certificate algorithm names that the Fast-DDS of Foxy, Galactic and Humble requires. `AUTO` turns them on when
  `ROS_DISTRO` is foxy, galactic or humble, which makes SROS2 work with Humble.
- Discovery settings `ROS_LOCALHOST_ONLY`, `ROS_AUTOMATIC_DISCOVERY_RANGE` and `ROS_STATIC_PEERS` with rcl's rules, and
  `ROS2NodeOptions.discoveryRange(...)` / `staticPeers(...)`.
- QoS presets `SENSOR_DATA`, `PARAMETERS`, `SERVICES_DEFAULT`, `PARAMETER_EVENTS`, `SYSTEM_DEFAULT`, `ROSOUT` and `CLOCK`;
  a `ROS2QoSProfile` copy constructor, `maxBlockingTime`, `DURATION_INFINITE`, `DURATION_UNSPECIFIED`, `isFrozen()`,
  `equals`, `hashCode` and `toString`.
- `AsyncROS2Publisher.offer()`, `getDroppedMessageCount()`, `getQueueCapacity()` and `getQueuedMessageCount()`;
  `AsyncROS2Node` constructors that take `ROS2NodeOptions` and a `ThreadFactory` for the publish thread, and
  `getPublishThread()`.
- `setStatisticsEnabled` / `isStatisticsEnabled` on publishers and subscriptions, and
  `ROS2Subscription.getMalformedSampleCount()`.
- New platforms: Linux riscv64, and Linux musl (Alpine) x86_64 and arm64. The musl libraries need `apk add libstdc++`.
- Native loader options: `JROS2_NATIVE_PATH` / `jros2.native.path` loads the libraries from a directory,
  `JROS2_NATIVE_EXTRACT_DIR` / `jros2.native.extract.dir` sets where they are copied, and `jros2.native.platform`
  overrides the detected platform. The copies are cached by content and reused across runs, with a fallback to
  `~/.cache/jros2` when the temporary directory is mounted `noexec`.
- Android: `ROS2Android.acquireMulticastLock(context)` for DDS discovery on Wi-Fi, the `CHANGE_WIFI_MULTICAST_STATE`
  permission in the AAR manifest, R8/ProGuard consumer rules in the AAR, and a third-party license file.
- Generator: the `dependencyPaths` input of `jros2GenTask` resolves types from other packages for the type hashes.
  `IDLObjectSequence.set(other, elementCopier)` makes a deep copy.
- `CDRDeserializationException` for malformed CDR input, and `CDRBuffer.beginRead(int)`.
- Generator: `format` of `jros2GenTask` selects the source code format of generated classes: `IHMC` (default) or `GOOGLE`, which
  applies [google-java-format](https://github.com/google/google-java-format). See `googleJavaFormatVersion`.
- `ROS2NodeOptions.shmSegmentSize(bytes)` and the `JROS2_SHM_SEGMENT_SIZE` setting (`jros2.shm.segment.size`): the
  shared-memory segment size of a participant (default 4 MiB, 0 for Fast-DDS's default transports).
- GC-free processes: `EpsilonGcAllocationTest` runs the control loop of the allocation gates on HotSpot with the Epsilon
  GC, without thread-local allocation buffers, and fails if the heap grows by one byte after the warm-up; the README
  has the recipe and a sizing rule ("GC-free processes (Epsilon)").
- Build and test tooling: reproducible Docker builds of the native libraries (`scripts/docker`) and the **Build natives**
  workflow, the `jros2GenerateDefaultInterfacesLocal` task, the `:jros2-test:allocationTest` allocation gate, and JaCoCo
  coverage (`:jros2-test:jacocoTestReport`, `:jros2-test:jacocoTestCoverageVerification`).

- Allocation-free hot paths, proven by zero-tolerance gates (`:jros2-test:allocationTest`, see the README): publishing
  (also concurrent and async), receiving (inline and executor callbacks, polling, `SampleInfo`), per-tick queries,
  statistics, and serialization, deserialization and `set()` of every message type allocate nothing in steady state,
  neither on the Java heap of any thread nor on the native heap of the hot-path threads.
- `ROS2CallbackExecutor`: an allocation-free `Executor` for subscription callbacks, with an optional `ThreadFactory` for
  real-time worker threads (the JDK's executors allocate per task).
- `ROS2Message.WithHeader`: generated messages with a `std_msgs/Header header` field implement it, so publisher age
  statistics read the header without reflection.
- Generated string setters take a `CharSequence` (`setName(CharSequence)`), and `CDRBuffer.newStringBuilder(...)` creates
  builders that stay allocation-free for non-Latin-1 text.
- Fast-DDS patches (`src/native/fastdds-patches`, applied by `scripts/docker/deps.sh` and `scripts/cppbuild.bash`; part of
  the dependency cache fingerprint): no heap allocation in Fast-DDS's steady-state publish and receive paths (keyless
  reader instance, shared-memory buffer handles, shared-memory watchdog, full shared-memory segment or port queue, payload
  pool growth, send buffers outside the pool). Participant profiles preallocate 80 network buffers per send buffer
  (`send_buffers.network_buffers_config`), so the gather vectors of the send buffers do not grow in steady state. Patch
  0008 is not about allocation: a reader of this process holds a counted reference to a writer of this process while it
  calls it, as writers already did for readers. Patch 0009 isn't either: a writer takes its reference to a reader of this
  process in one step, and takes the writer lock before it reads that reader in the intraprocess heartbeat. Patch 0010
  fixes a reader history lookup that missed a sample when a sample of another writer had the same source timestamp. The
  native libraries of all platforms are built with all ten patches.

### Changed
- A KEEP_LAST subscription preallocates as many payloads as its depth instead of Fast-DDS's default of 100
  (`allocated_samples`). Each payload holds the largest message seen, so a KEEP_LAST(10) subscription to 8 MB messages now
  uses about 90 MB instead of about 810 MB. Nothing changes on the wire.
- The first `publish()` on an `AsyncROS2Publisher` makes a deep copy of its message for every queue slot: it takes longer
  and uses the memory of a full queue at once (64 slots of a 1 MB image are 63 MB). A later message that is larger than the
  first still grows each slot once, when that slot is used.
- Generated classes no longer have a blank line before the end of each method, or several blank lines in a row.
- Publishing, receiving and statistics no longer use `synchronized` blocks on the hot path (a contended monitor makes
  HotSpot allocate natively); publisher statistics no longer allocate a varargs array per publish.
- `IDLStringSequence.add()` and `add(int)` return an empty builder (they returned one with stale contents).
- `ROS2QoSProfile.DEFAULT` and `new ROS2QoSProfile()` are RELIABLE, VOLATILE, KEEP_LAST(10) for subscriptions too, like
  rclcpp. Default subscriptions no longer receive from BEST_EFFORT publishers.
- The QoS presets are immutable; their setters throw `UnsupportedOperationException`.
- QoS is mapped like rmw_fastrtps. A `SYSTEM_DEFAULT` policy leaves the Fast-DDS default, so a publisher with
  `SYSTEM_DEFAULT` durability is TRANSIENT_LOCAL.
- Node names, namespaces and topic names are validated like rmw and throw `IllegalArgumentException` when invalid.
  Relative topic names are resolved against the node namespace.
- Creating a publisher or subscription from a subscription callback that runs on a Fast-DDS thread throws
  `IllegalStateException` when it would deadlock Fast-DDS, instead of hanging.
- `close()` called from a callback marks the node, context or endpoint closed and finishes the native teardown on a jros2
  thread after the callback returns.
- Callback subscriptions take every sample and call the callback once per sample; samples the callback does not read are
  consumed, as in rclcpp. `getUnreadMessageCount()` returns the Fast-DDS unread count.
- Message statistics are opt-in for subscriptions too (`setStatisticsEnabled(true)`).
- Bounded sequences throw `IllegalArgumentException` when they would exceed their bound, and a string containing a NUL
  character cannot be serialized.
- The `.msg` parser follows `rosidl_adapter` and fails generation on lines it used to ignore.
- `AsyncROS2Publisher` drops are counted, and `AsyncROS2Node.close()` writes the queued messages first (up to 2 s).
  The publish thread parks instead of spinning.
- One topic name can have only one message type per participant; a second type throws `IllegalArgumentException`.
- Linux native libraries are named by SONAME (`libfastcdr.so.2`, `libfastdds.so.3.6`), and every platform directory has a
  `natives.list` load-order manifest.
- The macOS and Windows native libraries are rebuilt with jros2's Fast-DDS patches and native ABI v2, without DDS-Security.
  macOS 12 is now the minimum (it was 14).
- The Linux, musl and Android native libraries are rebuilt from pinned sources: Fast-DDS 3.6.2 with DDS-Security and a
  private static OpenSSL 3.5.9, Fast-CDR 2.3.5, Release builds, glibc 2.31 baseline, Android NDK r30. Some are larger
  than before (linux-x86_64 grew from 12.8 MB to 19.0 MB, Android arm64-v8a from 13.4 MB to 18.1 MB), and the jar now
  also carries the three new Linux platforms.
- Every participant has one more DataWriter, for the ROS graph.
- Participants use jros2's shared-memory and UDPv4 transports, with a 4 MiB shared-memory segment, instead of Fast-DDS's
  builtin ones (512 KiB). Each participant takes about 4.2 MiB of `/dev/shm`; with less than twice that free, it falls back
  to Fast-DDS's default segment with a warning. Nothing changes on the wire or in discovery. `JROS2_SHM_SEGMENT_SIZE=0`
  restores the builtin transports, which `FASTDDS_BUILTIN_TRANSPORTS` then configures; that variable also keeps them when
  no segment size is set. Custom transports and Android (UDPv4) are unchanged.

### Deprecated
- `ROS2Node.createService`, `declareParameter` and `getParameter`. They throw `UnsupportedOperationException`.
- The protected field `ROS2Publisher.closeLock`, which is no longer used.

### Known limitations
- The macOS and Windows native libraries have no DDS-Security, so SROS2 is not available on these platforms: `Enforce`
  fails and `Permissive` runs unsecured.
- On macOS and Windows the shared-memory segments are files on disk, not in `/dev/shm`, so the fallback to Fast-DDS's
  default segment when `/dev/shm` is short of space does not apply there.
- On macOS a process can have about 250 participants: macOS allows 2048 threads per process (`kern.num_taskthreads`) and a
  participant has about 8. Beyond that a C++ exception that nothing catches aborts the JVM. Share participants with
  `ROS2Context`.
- Large messages from native ROS 2 publishers with default settings (512 KiB shared-memory segment) still reach jros2
  subscriptions on the same host after Fast-DDS's repair delay, or not at all with BEST_EFFORT; the publisher's segment
  decides (see the README, "Large messages and shared memory").
- The allocation gates measure HotSpot on Linux; on Android (ART), macOS and Windows the same code paths run, but are not
  measured.
- No SROS2 interop with Foxy or Galactic. With Foxy or Galactic in the domain, set `ROS_DISTRO` (see
  [ROS 2 distro compatibility](README.md#ros-2-distro-compatibility)).
- The bundled interfaces are the Humble definitions. `sensor_msgs/Range` therefore has no `variance` field and advertises
  the Humble type hash.
