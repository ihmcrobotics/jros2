/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2.allocation.nativeheap;

import sensor_msgs.Image;
import sensor_msgs.JointState;
import us.ihmc.fastddsjava.fastddsjavaTools;
import us.ihmc.fastddsjava.natives.fastddsjava;
import us.ihmc.fastddsjava.natives.fastddsjavaCallback;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.TransportDescriptorTypeTools;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.jros2.AsyncROS2Node;
import us.ihmc.jros2.ROS2Message;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2NodeOptions;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2QoSProfile;
import us.ihmc.jros2.ROS2Subscription;
import us.ihmc.jros2.ROS2SubscriptionCallback;
import us.ihmc.jros2.ROS2Topic;
import us.ihmc.jros2.SampleInfo;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Child JVM of {@link NativeHeapAllocationTest}, started with the {@code alloccount} LD_PRELOAD shim. Runs, after a
 * warm-up of the same work, each in its own armed window: a raw JNI loop over every hot-path native entry point, the
 * hot-path matrix (RELIABLE/BEST_EFFORT x small sensor_msgs/JointState / large fragmented sensor_msgs/Image x sync
 * ROS2Publisher / AsyncROS2Publisher x inline callback / callback executor / polling), all small topics at once from
 * three concurrent publisher threads, publishing on topics without subscribers, fan-out / fan-in (two publishers and three
 * subscriptions on one topic, small and large messages), seven seconds of steady RELIABLE traffic (longer than the periodic
 * Fast-DDS activity it triggers), a subscriber too slow for its publisher (full shared-memory port queue) and seven seconds
 * without traffic (background activity of every Fast-DDS thread). With
 * jros2's default transports and intraprocess delivery OFF (see {@link #overflowWindows()}) two more windows send 2 MB
 * images from a node with Fast-DDS's default 512 KiB shared-memory segment, which overflows. Prints:
 * <pre>
 * NHA WINDOW &lt;window#attempt&gt; sent=.. received=.. totalCalls=.. droppedThreads=.. unslottedCalls=.. shimInternalCalls=..
 * NHA THREAD &lt;window#attempt&gt; &lt;class&gt; &lt;tid&gt; &lt;name&gt; malloc=.. calloc=.. realloc=.. memalign=.. new=.. free=.. mmap=.. ...
 * NHA STACK  &lt;window#attempt&gt; &lt;tid&gt; &lt;name&gt; &lt;kind&gt; size=.. count=.. &lt;category&gt; | frame | frame ...
 * NHA JAVA   &lt;window#attempt&gt; &lt;java thread&gt; bytes=..   (Java heap bytes of hot-path threads, reported)
 * NHA RESULT &lt;window&gt; gated=.. hotPathCalls=.. javaNativeCalls=.. hotPathJvmInternalCalls=.. monitorInflationCalls=.. attempts=.. ...
 * NHA CONFIG intraprocessDelivery=.. nativeAbiV2=.. nativeAbiVersion=..   (what the library actually uses)
 * NHA CALLBACK &lt;window&gt; thread=..   (the thread that ran the inline / executor callbacks of a matrix window)
 * NHA SELFTEST ..., NHA DONE
 * </pre>
 * Thread classes: {@code hot-java} (the threads this child drives: "hp-*"), {@code fastdds} (every thread Fast-DDS
 * creates: "dds.*", also once attached to the JVM as "fastdds-callback") and {@code jvm} (compiler, GC, VM threads and
 * the observer thread "main"; reported as background). Every distinct native stack is recorded with its count and
 * attributed by {@link #category}:
 * <ul>
 *    <li>{@code library}: the JNI layer or Fast-DDS (libjnifastddsjava, libfastdds, libfastcdr, also through libstdc++)
 *    made the call, or a JVM function that this code called directly (a JNI function, attaching a thread) did;</li>
 *    <li>{@code java-native}: a JVM or JDK native method that Java code called (Unsafe.allocateMemory, a JDK native
 *    library, a JVM_* entry point);</li>
 *    <li>{@code jvm-monitor}: HotSpot monitor inflation (a contended synchronized block or Object.wait; again after every
 *    deflation of the idle monitor);</li>
 *    <li>{@code discovery}: library calls inside Fast-DDS discovery processing (a participant or endpoint joining,
 *    leaving or changing);</li>
 *    <li>purely JVM-internal work for the Java code that runs: {@code jvm-jit} (compile requests, on-stack replacement,
 *    deoptimization, call site resolution), {@code jvm-handshake} (a handshake or safepoint operation another thread
 *    requested), {@code jvm-gc} (garbage collector barrier buffers), and {@code jvm-internal} (anything else).</li>
 * </ul>
 * <p>
 * Gate, per window, on the hot-path threads (hot-java and fastdds), summed over EVERY attempt: zero {@code library},
 * {@code java-native}, {@code jvm-monitor} and {@code jvm-internal} calls ({@code hotPathCalls}, {@code javaNativeCalls},
 * {@code monitorInflationCalls}, {@code hotPathJvmInternalCalls}), zero Java heap bytes created by JNI code in the raw
 * JNI window ({@code jniJavaBytes}), and the shim lost nothing and allocated nothing itself. No attempt is excused, so a
 * single library malloc in any attempt fails the window. {@code jvm-jit}, {@code jvm-handshake} and {@code jvm-gc} calls
 * are purely JVM-internal: counted and reported separately with their stacks ({@code jitCalls}, {@code handshakeCalls},
 * {@code gcCalls}), not gated.
 * <p>
 * The only reason to measure a window again: an attempt in which Fast-DDS processed discovery data ({@code discovery}
 * stacks on a hot-path thread: e.g. a participant created just before the window still being matched, or
 * another process joining the domain) is disturbed. Its {@code discovery} calls are reported ({@code discoveryCalls}),
 * the window is measured again, up to {@value #MAX_ATTEMPTS} attempts, and fails if no attempt was undisturbed (then the
 * discovery calls of the last attempt count as library calls). In a disturbed attempt, the library calls of a thread
 * that made discovery calls in that attempt count as discovery too ("NHA FALLOUT" lines: work for the joining or
 * leaving participant that its stacks cannot attribute, e.g. BaseReader::update_last_notified for a new SPDP writer on
 * the SPDP receive thread); every call of every other thread of a disturbed attempt still counts as above, and the
 * undisturbed attempt is gated in full.
 */
public final class NativeHeapAllocationChild
{
   static final String PREFIX = "NHA ";
   /** Transport variant of the jros2 nodes: "default" or "udp" (see {@link #nodeOptions()}). */
   static final String TRANSPORT_PROPERTY = "nha.transport";

   // Warm-ups long enough for the C2 compilation of the per-message code (tiered thresholds: thousands of calls), so that
   // compile requests (CompileTask, constant pool resolution on the requesting thread) do not fall into the window.
   private static final int SMALL_WARMUP = 12000;
   private static final int SMALL_MEASURED = 3000;
   private static final long SMALL_PACE_NANOS = TimeUnit.MICROSECONDS.toNanos(40);
   // Large messages run loop-heavy methods (2 MB byte sequences) a few times per message: those run C1 code plus on-stack
   // replacement (which mallocs a migration buffer per entry) until the method itself is C2-compiled, after hundreds of
   // invocations (Tier4MinInvocationThreshold 600).
   private static final int LARGE_WARMUP = 700;
   private static final long LARGE_WARMUP_PACE_NANOS = TimeUnit.MILLISECONDS.toNanos(4);
   private static final int LARGE_MEASURED = 40;
   private static final long LARGE_PACE_NANOS = TimeUnit.MILLISECONDS.toNanos(8);
   // 2 MB images: every shared-memory segment of the variants holds one (jros2's default 4 MiB, the "shm" variant's
   // 8500 KiB); the overflow windows send them through Fast-DDS's default 512 KiB segment.
   private static final int IMAGE_WIDTH = 1920;
   private static final int IMAGE_HEIGHT = 1080;
   private static final int JOINTS = 30;
   private static final int DEPTH = 10;
   private static final long QUIET_WINDOW_MILLIS = 7000;
   static final int MAX_ATTEMPTS = 3;
   private static final int COMBINED_ROUNDS = 300;
   private static final int COMBINED_WARMUP_PHASES = 12;
   private static final long POLL_PACE_NANOS = 20_000;

   enum Receive
   {
      INLINE, EXECUTOR, POLLING
   }

   private static NativeAllocationCounter counter;
   private static final ThreadMXBeanAccess javaBytes = new ThreadMXBeanAccess();
   private static final Worker publisherWorker = new Worker("hp-pub");
   private static final Worker pollWorker = new Worker("hp-poll");
   private static final List<AutoCloseable> closeables = new ArrayList<>();
   private static int domainId;
   private static ThreadPoolExecutor callbackExecutor;
   private static boolean anyFailure = false;

   public static void main(String[] args) throws Exception
   {
      counter = NativeAllocationCounter.open();
      if (counter == null)
      {
         System.out.println(PREFIX + "ERROR the alloccount shim is not loaded");
         System.exit(2);
      }
      fastddsjavaNativeLibrary.load();
      domainId = Integer.parseInt(System.getenv().getOrDefault("ROS_DOMAIN_ID", "0"));
      String only = System.getProperty("nha.only", "");

      selfTest();
      if (selected(only, "raw"))
         rawJniWindow();

      ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1024), r -> new Thread(r, "hp-exec"));
      executor.prestartAllCoreThreads();
      callbackExecutor = executor;
      ROS2Node syncPublisherNode = new ROS2Node("nha_pub", nodeOptions());
      AsyncROS2Node asyncPublisherNode = new AsyncROS2Node("nha_apub", nodeOptions(), r -> new Thread(r, "hp-async-pub"));
      ROS2Node subscriberNode = new ROS2Node("nha_sub", nodeOptions());
      ROS2Node executorSubscriberNode = new ROS2Node("nha_esub", nodeOptions().callbackExecutor(executor));
      // The publisher of the overflow windows: the Fast-DDS builtin transports, whose 512 KiB shared-memory segment (in
      // which the publisher writes) cannot hold a 2 MB image
      ROS2Node overflowPublisherNode = overflowWindows() && selected(only, "overflow")
            ? new ROS2Node("nha_overflow_pub", nodeOptions().shmSegmentSize(ROS2NodeOptions.FASTDDS_DEFAULT_SHM_SEGMENT_SIZE))
            : null;
      printConfiguration();
      // The shared-memory watchdog adds the segments of new participants to its watch list once per second.
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1500));
      List<Runnable> combined = new ArrayList<>();
      try
      {
         for (ROS2QoSProfile.Reliability reliability : new ROS2QoSProfile.Reliability[] {ROS2QoSProfile.Reliability.RELIABLE,
                                                                                         ROS2QoSProfile.Reliability.BEST_EFFORT})
         {
            for (boolean large : new boolean[] {false, true})
            {
               for (boolean async : new boolean[] {false, true})
               {
                  for (Receive receive : Receive.values())
                  {
                     String name = (reliability == ROS2QoSProfile.Reliability.RELIABLE ? "rel" : "be") + "-" + (large ? "image" : "joints") + "-"
                                   + (async ? "async" : "sync") + "-" + receive.name().toLowerCase(Locale.ROOT);
                     if (!selectedMatrixWindow(only, name))
                        continue;
                     ROS2Node publisherNode = async ? asyncPublisherNode : syncPublisherNode;
                     ROS2Node receiverNode = receive == Receive.EXECUTOR ? executorSubscriberNode : subscriberNode;
                     Runnable publishOne = large
                           ? scenario(name, reliability, publisherNode, receiverNode, receive, Image.class, NativeHeapAllocationChild::matrixImage,
                                      Image::new, LARGE_WARMUP, LARGE_MEASURED, LARGE_PACE_NANOS)
                           : scenario(name, reliability, publisherNode, receiverNode, receive, JointState.class, NativeHeapAllocationChild::jointState,
                                      JointState::new, SMALL_WARMUP, SMALL_MEASURED, SMALL_PACE_NANOS);
                     if (!large)
                        combined.add(publishOne);
                  }
               }
            }
         }

         if (overflowPublisherNode != null)
         {
            // 2 MB images through the 512 KiB segment: most messages (fragments) overflow it and are discarded by the
            // sender, an error path that happens in steady state. Gated like the matrix, but nothing needs to arrive.
            for (ROS2QoSProfile.Reliability reliability : new ROS2QoSProfile.Reliability[] {ROS2QoSProfile.Reliability.RELIABLE,
                                                                                            ROS2QoSProfile.Reliability.BEST_EFFORT})
            {
               String name = OVERFLOW_PREFIX + (reliability == ROS2QoSProfile.Reliability.RELIABLE ? "rel" : "be") + "-image-sync-inline";
               scenario(name, reliability, overflowPublisherNode, subscriberNode, Receive.INLINE, Image.class, () -> image(IMAGE_WIDTH, IMAGE_HEIGHT), Image::new,
                        LARGE_WARMUP, LARGE_MEASURED, LARGE_PACE_NANOS);
            }
         }
         if (selected(only, "combined"))
            combinedWindow(combined);
         if (selected(only, "unmatched"))
            unmatchedWindow(syncPublisherNode, asyncPublisherNode);
         if (selected(only, "fan"))
         {
            fanWindow("fan-joints", ROS2QoSProfile.Reliability.RELIABLE, syncPublisherNode, asyncPublisherNode, subscriberNode, executorSubscriberNode,
                      JointState.class, NativeHeapAllocationChild::jointState, JointState::new, SMALL_WARMUP, SMALL_MEASURED, SMALL_PACE_NANOS);
            // Two images per round to three readers: a slower pace than the matrix (the methods are compiled by now).
            fanWindow("fan-image", ROS2QoSProfile.Reliability.RELIABLE, syncPublisherNode, asyncPublisherNode, subscriberNode, executorSubscriberNode,
                      Image.class, NativeHeapAllocationChild::matrixImage, Image::new, 250, LARGE_MEASURED, 3 * LARGE_PACE_NANOS);
         }
         if (selected(only, "soak"))
            soakWindow(syncPublisherNode, subscriberNode);
         if (selected(only, "slow"))
            slowReaderWindow(syncPublisherNode);
         if (selected(only, "quiet"))
            quietWindow();
      }
      finally
      {
         for (int i = closeables.size() - 1; i >= 0; --i)
            closeables.get(i).close();
         if (overflowPublisherNode != null)
            overflowPublisherNode.close();
         executorSubscriberNode.close();
         subscriberNode.close();
         asyncPublisherNode.close();
         syncPublisherNode.close();
         executor.shutdownNow();
      }
      System.out.println(PREFIX + "DONE failures=" + anyFailure);
      System.out.flush();
      System.exit(0);
   }

   /** Window name prefix of the shared-memory overflow windows (see {@link #overflowWindows()}). */
   static final String OVERFLOW_PREFIX = "overflow-";

   /** Names of the 24 windows of the hot-path matrix, in the order the child runs them. */
   static List<String> matrixWindowNames()
   {
      List<String> names = new ArrayList<>();
      for (String reliability : new String[] {"rel", "be"})
      {
         for (String size : new String[] {"joints", "image"})
         {
            for (String publish : new String[] {"sync", "async"})
            {
               for (Receive receive : Receive.values())
                  names.add(reliability + "-" + size + "-" + publish + "-" + receive.name().toLowerCase(Locale.ROOT));
            }
         }
      }
      return names;
   }

   /**
    * Window selection of the system property {@code nha.only} (a development aid; empty, the default, runs every window):
    * a window group runs when the selection contains its key ("raw", "overflow", "combined", "unmatched", "fan", "soak",
    * "slow", "quiet").
    */
   static boolean selected(String only, String key)
   {
      return only.isEmpty() || only.contains(key);
   }

   /** Like {@link #selected}, for a matrix window: its full name, or "matrix" for all of them. */
   static boolean selectedMatrixWindow(String only, String name)
   {
      return only.isEmpty() || only.contains(name) || only.equals("matrix");
   }

   /**
    * Prints the configuration the library actually uses (not just what the parent asked for), so that the parent can
    * verify that the intraprocess mode and the native ABI path of the configuration are in effect:
    * {@code NHA CONFIG intraprocessDelivery=<USER_DATA_ONLY|OFF> nativeAbiV2=<true|false> nativeAbiVersion=<n>}.
    */
   private static void printConfiguration() throws Exception
   {
      java.lang.reflect.Field intraprocessField = ProfilesXML.class.getDeclaredField("intraprocessDelivery");
      intraprocessField.setAccessible(true);
      Class<?> endpointSupport = Class.forName("us.ihmc.jros2.EndpointSupport");
      java.lang.reflect.Method useNativeAbiV2 = endpointSupport.getDeclaredMethod("useNativeAbiV2");
      useNativeAbiV2.setAccessible(true);
      System.out.println(PREFIX + "CONFIG intraprocessDelivery=" + intraprocessField.get(null) + " nativeAbiV2=" + useNativeAbiV2.invoke(null)
                         + " nativeAbiVersion=" + fastddsjava.abiVersion());
   }

   /** The first thread that ran a subscription callback of a window (recorded during its warm-up). */
   private static final Map<String, String> callbackThreads = new HashMap<>();

   /**
    * Prints the thread that ran the callbacks of an inline-callback window: with intraprocess delivery USER_DATA_ONLY the
    * publishing thread (hp-pub, hp-async-pub), otherwise a Fast-DDS thread ("fastdds-callback"); the parent verifies it.
    */
   private static void printCallbackThread(String window, Thread thread)
   {
      String name = thread == null ? "none" : thread.getName();
      callbackThreads.put(window, name);
      System.out.println(PREFIX + "CALLBACK " + window + " thread=" + name.replace(' ', '_'));
   }

   /**
    * True with jros2's default transports and intraprocess delivery OFF: samples between the nodes of this process go
    * through shared memory, and the overflow windows send 2 MB images from a node with Fast-DDS's default 512 KiB segment.
    */
   static boolean overflowWindows()
   {
      return !intraprocess() && "default".equals(System.getProperty(TRANSPORT_PROPERTY, "default"));
   }

   /** Shared-memory segment of the "shm" variant: the size Fast-DDS uses for its LARGE_DATA builtin transports. */
   static final long SHM_SEGMENT_SIZE = 8500L * 1024L;

   /**
    * Node options of the selected transport variant (system property {@value #TRANSPORT_PROPERTY}):
    * <ul>
    *    <li>"shm": shared memory with a segment that holds a burst of large messages ({@value #SHM_SEGMENT_SIZE} bytes, as
    *    Fast-DDS's LARGE_DATA, and a port queue to match) plus UDPv4, given as custom transports.</li>
    *    <li>"udp": UDPv4 only, which is what traffic between hosts uses.</li>
    *    <li>"default": jros2's default transports (shared memory with a 4 MiB segment, UDPv4), as jros2 configures them when
    *    no transport is given.</li>
    * </ul>
    */
   private static ROS2NodeOptions nodeOptions()
   {
      ROS2NodeOptions options = ROS2NodeOptions.defaults();
      switch (System.getProperty(TRANSPORT_PROPERTY, "default"))
      {
         case "shm" -> options.transports(largeSegmentSharedMemory(), TransportDescriptorTypeTools.createUDPv4Transport());
         case "udp" -> options.transports(TransportDescriptorTypeTools.createUDPv4Transport());
         default ->
         {
            // jros2's default transports
         }
      }
      return options;
   }

   private static TransportDescriptorType largeSharedMemory;

   private static TransportDescriptorType largeSegmentSharedMemory()
   {
      if (largeSharedMemory == null)
      {
         largeSharedMemory = TransportDescriptorTypeTools.copyOf(TransportDescriptorTypeTools.createSHMDescriptor(), "nha_shm_large");
         largeSharedMemory.setSegmentSize(SHM_SEGMENT_SIZE);
         // One queue cell per KiB of segment, like LARGE_DATA (512 cells per 512 KiB by default).
         largeSharedMemory.setPortQueueCapacity(SHM_SEGMENT_SIZE / 1024L);
      }
      return largeSharedMemory;
   }

   /* ------------------------------------------------------------------------------------------------------------ */

   /** Verifies the shim sees a native allocation made by the JVM for this thread (Unsafe.allocateMemory -> malloc). */
   private static void selfTest() throws Exception
   {
      AtomicLong tid = new AtomicLong();
      publisherWorker.runAndWait(() -> tid.set(currentTid()));
      publisherWorker.runAndWait(() ->
                                 {
                                    counter.arm();
                                    ByteBuffer direct = ByteBuffer.allocateDirect(12345);
                                    direct.put(0, (byte) 1);
                                    counter.disarm();
                                 });
      NativeAllocationCounter.Window window = counter.disarmAndRead();
      NativeAllocationCounter.ThreadCounts thread = window.thread((int) tid.get());
      long mallocs = thread == null ? 0 : thread.malloc + thread.calloc + thread.memalign;
      boolean sawSize = false;
      if (thread != null)
      {
         for (NativeAllocationCounter.Stack stack : thread.allocationStacks)
            sawSize |= stack.size >= 12345;
      }
      System.out.println(PREFIX + "SELFTEST allocateDirect mallocs=" + mallocs + " sawRequestedSize=" + sawSize + " tidMatched=" + (thread != null)
                         + " shimInternalCalls=" + window.internalCalls);
   }

   /* ------------------------------------------------------------------------------------------------------------ */

   private static <T extends ROS2Message<T>> Runnable scenario(String name,
                                                               ROS2QoSProfile.Reliability reliability,
                                                               ROS2Node publisherNode,
                                                               ROS2Node subscriberNode,
                                                               Receive receive,
                                                               Class<T> type,
                                                               Supplier<T> messageFactory,
                                                               Supplier<T> receiveFactory,
                                                               int warmup,
                                                               int measured,
                                                               long paceNanos) throws Exception
   {
      boolean large = type == Image.class;
      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.reliability(reliability);
      qos.history(ROS2QoSProfile.History.KEEP_LAST);
      qos.depth(DEPTH);
      qos.durability(ROS2QoSProfile.Durability.VOLATILE);

      ROS2Topic<T> topic = new ROS2Topic<>("/nha/" + name.replace('-', '_'), type);
      T message = messageFactory.get();
      T received = receiveFactory.get();
      SampleInfo sampleInfo = new SampleInfo();
      AtomicLong receivedCount = new AtomicLong();
      Thread[] callbackThread = new Thread[1];

      ROS2Subscription<T> subscription;
      if (receive == Receive.POLLING)
      {
         subscription = subscriberNode.createSubscription(topic, qos);
      }
      else
      {
         ROS2SubscriptionCallback<T> callback = reader ->
         {
            if (callbackThread[0] == null)
               callbackThread[0] = Thread.currentThread();
            // Alternate the two read flavors
            boolean ok = (receivedCount.get() & 1) == 0 ? reader.read(received) : reader.read(received, sampleInfo);
            if (ok)
               receivedCount.incrementAndGet();
         };
         subscription = subscriberNode.createSubscription(topic, callback, qos);
      }
      ROS2Publisher<T> publisher = publisherNode.createPublisher(topic, qos);
      if (!publisher.waitForSubscription(10_000) || !subscription.waitForPublisher(10_000))
         throw new IllegalStateException(name + ": endpoints did not match");
      if (receive == Receive.EXECUTOR)
      {
         // Statistics read the sample info timestamps through JNI on every sample.
         publisher.setStatisticsEnabled(true);
         subscription.setStatisticsEnabled(true);
      }

      Poller<T> poller = receive == Receive.POLLING ? new Poller<>(subscription, received, sampleInfo, receivedCount) : null;
      Runnable publishLoop = new Runnable()
      {
         int sequence = 0;

         @Override
         public void run()
         {
            stamp(message, ++sequence);
            publisher.publish(message);
            publisher.getPublicationMatchedStatus();
         }
      };

      // Warm-up, not counted. First a burst that fills the reader history while nothing takes from it (polling) or the
      // callbacks lag (executor): Fast-DDS's payload pool (PREALLOCATED_WITH_REALLOC) grows a pool node, or adds one, the
      // first time more samples than ever before are in use at once (writer history + reader history), a high-water mark
      // that a robot's own warm-up reaches the same way. Then the same work as measured.
      CountDownLatch executorBlocked = new CountDownLatch(1);
      if (receive == Receive.EXECUTOR)
         callbackExecutor.execute(() -> awaitUninterruptibly(executorBlocked));
      publisherWorker.runAndWait(publishLoop, 2 * DEPTH, 0);
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(large ? 300 : 100));
      executorBlocked.countDown();
      runPhase(publishLoop, warmup, large ? LARGE_WARMUP_PACE_NANOS : paceNanos, poller, receivedCount);
      if (receive != Receive.POLLING)
         printCallbackThread(name, callbackThread[0]);
      gatedWindow(name, measured, receivedCount, () -> runPhase(publishLoop, measured, paceNanos, poller, receivedCount), null);

      closeables.add(() ->
                     {
                        publisherNode.destroyPublisher(publisher);
                        subscriberNode.destroySubscription(subscription);
                     });
      final Poller<T> finalPoller = poller;
      return () ->
      {
         publishLoop.run();
         if (finalPoller != null)
            finalPoller.run();
      };
   }

   /** Publishes {@code count} messages on hp-pub with the given pace while hp-poll polls, then waits for the receivers. */
   private static void runPhase(Runnable publishOne, int count, long paceNanos, Poller<?> poller, AtomicLong receivedCount)
   {
      if (poller != null)
         pollWorker.start(poller, Integer.MAX_VALUE, POLL_PACE_NANOS);
      publisherWorker.runAndWait(publishOne, count, paceNanos);
      // Let the receive side drain: wait until the received count is stable.
      long last = -1;
      long stableSince = System.nanoTime();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (System.nanoTime() < deadline)
      {
         long now = receivedCount.get();
         if (now != last)
         {
            last = now;
            stableSince = System.nanoTime();
         }
         else if (System.nanoTime() - stableSince > TimeUnit.MILLISECONDS.toNanos(300))
         {
            break;
         }
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
      }
      if (poller != null)
      {
         pollWorker.stop();
         pollWorker.awaitIdle();
      }
   }

   private static void awaitUninterruptibly(CountDownLatch latch)
   {
      boolean done = false;
      while (!done)
      {
         try
         {
            done = latch.await(10, TimeUnit.SECONDS);
         }
         catch (InterruptedException e)
         {
            // keep waiting
         }
      }
   }

   /** Polling receive on hp-poll: per tick, the unread count, then read, read with SampleInfo or readLatest until empty. */
   private static final class Poller<T extends ROS2Message<T>> implements Runnable
   {
      private final ROS2Subscription<T> subscription;
      private final T received;
      private final SampleInfo sampleInfo;
      private final AtomicLong receivedCount;
      private int tick;

      Poller(ROS2Subscription<T> subscription, T received, SampleInfo sampleInfo, AtomicLong receivedCount)
      {
         this.subscription = subscription;
         this.received = received;
         this.sampleInfo = sampleInfo;
         this.receivedCount = receivedCount;
      }

      @Override
      public void run()
      {
         while (poll())
         {
            // drain
         }
      }

      boolean poll()
      {
         if (subscription.getUnreadMessageCount() == 0)
            return false;
         boolean got;
         switch (tick++ % 3)
         {
            case 0 -> got = subscription.read(received);
            case 1 -> got = subscription.read(received, sampleInfo);
            default ->
            {
               int count = subscription.readLatest(received);
               got = count > 0;
               if (got)
                  receivedCount.addAndGet(count - 1);
            }
         }
         if (got)
            receivedCount.incrementAndGet();
         return got;
      }
   }

   /* ------------------------------------------------------------------------------------------------------------ */

   /** All small topics at once: three publisher threads in parallel (contention on shared native state) plus polling. */
   private static void combinedWindow(List<Runnable> publishers) throws Exception
   {
      if (publishers.isEmpty())
         return;
      Worker[] workers = {publisherWorker, new Worker("hp-pub2"), new Worker("hp-pub3")};
      Runnable round = () ->
      {
         for (int p = 0; p < publishers.size(); ++p)
            publishers.get(p).run();
      };
      Runnable phase = () ->
      {
         for (Worker worker : workers)
            worker.start(round, COMBINED_ROUNDS, SMALL_PACE_NANOS);
         for (Worker worker : workers)
            worker.awaitIdle();
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
      };
      // Warm-up of the concurrent pattern: new receiver types at shared call sites, and branches only contention takes
      // (concurrent publishers on one AsyncROS2Publisher, a dispatch already running), deoptimize and recompile compiled
      // code a few times (up to PerBytecodeTrapLimit traps per call site) before the code is stable.
      for (int i = 0; i < COMBINED_WARMUP_PHASES; ++i)
         phase.run();
      gatedWindow("combined-concurrent", 3L * COMBINED_ROUNDS * publishers.size(), null, phase, null);
   }

   /**
    * Publishing on topics nobody subscribes to, which a robot does all the time (topics only a logger or a GUI listens to
    * now and then): RELIABLE JointState from the sync publisher, BEST_EFFORT JointState from the AsyncROS2Publisher and a
    * RELIABLE large image every 64 rounds from the sync publisher. Without a matched reader Fast-DDS only keeps the
    * samples in the writer history (KEEP_LAST), recycling its payloads.
    */
   private static void unmatchedWindow(ROS2Node syncPublisherNode, ROS2Node asyncPublisherNode) throws Exception
   {
      Runnable joints = unmatchedPublisher("rel_joints", ROS2QoSProfile.Reliability.RELIABLE, syncPublisherNode, JointState.class, jointState());
      Runnable asyncJoints = unmatchedPublisher("be_joints", ROS2QoSProfile.Reliability.BEST_EFFORT, asyncPublisherNode, JointState.class, jointState());
      Runnable image = unmatchedPublisher("rel_image", ROS2QoSProfile.Reliability.RELIABLE, syncPublisherNode, Image.class, matrixImage());
      Runnable round = new Runnable()
      {
         int rounds = 0;

         @Override
         public void run()
         {
            joints.run();
            asyncJoints.run();
            if ((rounds++ & 63) == 0)
               image.run();
         }
      };
      Runnable phase = () ->
      {
         publisherWorker.runAndWait(round, SMALL_MEASURED, SMALL_PACE_NANOS);
         // The AsyncROS2Publisher thread drains its queue
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
      };
      publisherWorker.runAndWait(round, SMALL_WARMUP, SMALL_PACE_NANOS);
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
      gatedWindow("unmatched-publish", 2L * SMALL_MEASURED + SMALL_MEASURED / 64, null, phase, null);
   }

   private static <T extends ROS2Message<T>> Runnable unmatchedPublisher(String name, ROS2QoSProfile.Reliability reliability, ROS2Node node, Class<T> type, T message)
   {
      ROS2Topic<T> topic = new ROS2Topic<>("/nha/unmatched_" + name, type);
      ROS2Publisher<T> publisher = node.createPublisher(topic, qos(reliability));
      closeables.add(() -> node.destroyPublisher(publisher));
      System.out.println(PREFIX + "UNMATCHED " + name + " matched=" + publisher.getPublicationMatchedStatus());
      return new Runnable()
      {
         int sequence = 0;

         @Override
         public void run()
         {
            stamp(message, ++sequence);
            publisher.publish(message);
            publisher.getPublicationMatchedStatus();
         }
      };
   }

   /**
    * Fan-out and fan-in on one topic: two publishers (the sync ROS2Publisher and an AsyncROS2Publisher, on two nodes) and
    * three subscriptions (inline callback reading with SampleInfo, callback executor, polling; on three nodes, one of them
    * the sync publisher's own), so that every sample goes to several readers (several locators / ports per send) and every
    * reader has several writers (writer proxies, the instance's writer list).
    */
   private static <T extends ROS2Message<T>> void fanWindow(String name,
                                                            ROS2QoSProfile.Reliability reliability,
                                                            ROS2Node syncNode,
                                                            ROS2Node asyncNode,
                                                            ROS2Node inlineNode,
                                                            ROS2Node executorNode,
                                                            Class<T> type,
                                                            Supplier<T> messageFactory,
                                                            Supplier<T> receiveFactory,
                                                            int warmup,
                                                            int measured,
                                                            long paceNanos) throws Exception
   {
      boolean large = type == Image.class;
      ROS2QoSProfile qos = qos(reliability);
      ROS2Topic<T> topic = new ROS2Topic<>("/nha/" + name.replace('-', '_'), type);
      AtomicLong receivedCount = new AtomicLong();
      T inlineReceived = receiveFactory.get();
      SampleInfo inlineInfo = new SampleInfo();
      T executorReceived = receiveFactory.get();
      T polled = receiveFactory.get();
      SampleInfo polledInfo = new SampleInfo();
      ROS2Subscription<T> inline = inlineNode.createSubscription(topic, reader ->
      {
         if (reader.read(inlineReceived, inlineInfo))
            receivedCount.incrementAndGet();
      }, qos);
      ROS2Subscription<T> executor = executorNode.createSubscription(topic, reader ->
      {
         if (reader.read(executorReceived))
            receivedCount.incrementAndGet();
      }, qos);
      ROS2Subscription<T> polling = syncNode.createSubscription(topic, qos);
      ROS2Publisher<T> syncPublisher = syncNode.createPublisher(topic, qos);
      ROS2Publisher<T> asyncPublisher = asyncNode.createPublisher(topic, qos);
      closeables.add(() ->
                     {
                        syncNode.destroyPublisher(syncPublisher);
                        asyncNode.destroyPublisher(asyncPublisher);
                        inlineNode.destroySubscription(inline);
                        executorNode.destroySubscription(executor);
                        syncNode.destroySubscription(polling);
                     });
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while ((syncPublisher.getPublicationMatchedStatus() < 3 || asyncPublisher.getPublicationMatchedStatus() < 3) && System.nanoTime() < deadline)
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
      if (syncPublisher.getPublicationMatchedStatus() < 3 || asyncPublisher.getPublicationMatchedStatus() < 3)
         throw new IllegalStateException(name + ": endpoints did not match");

      T syncMessage = messageFactory.get();
      T asyncMessage = messageFactory.get();
      Poller<T> poller = new Poller<>(polling, polled, polledInfo, receivedCount);
      Runnable publishOne = new Runnable()
      {
         int sequence = 0;

         @Override
         public void run()
         {
            ++sequence;
            stamp(syncMessage, sequence);
            syncPublisher.publish(syncMessage);
            stamp(asyncMessage, sequence);
            asyncPublisher.publish(asyncMessage);
         }
      };

      // Fill the histories once while nothing takes from them (see scenario), then the same work as measured.
      CountDownLatch executorBlocked = new CountDownLatch(1);
      callbackExecutor.execute(() -> awaitUninterruptibly(executorBlocked));
      publisherWorker.runAndWait(publishOne, 2 * DEPTH, 0);
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(large ? 300 : 100));
      executorBlocked.countDown();
      runPhase(publishOne, warmup, paceNanos, poller, receivedCount);
      gatedWindow(name, 2L * 3 * measured, receivedCount, () -> runPhase(publishOne, measured, paceNanos, poller, receivedCount), null);
   }

   /** Steady traffic of the soak window: ticks of {@value #SOAK_TICK_NANOS} ns, an image every {@value #SOAK_IMAGE_EVERY} ticks. */
   private static final long SOAK_TICK_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
   private static final int SOAK_IMAGE_EVERY = 32;
   private static final int SOAK_WARMUP_TICKS = 3000;
   private static final int SOAK_TICKS = 7000;

   /**
    * Steady RELIABLE traffic for longer than the periodic Fast-DDS activity that traffic triggers (periodic heartbeats and
    * the acknowledgements they ask for: 3 s period by default; participant announcements) and than events that recur
    * every few thousand messages: JointState every ~1 ms plus an image every {@value #SOAK_IMAGE_EVERY} ms, both with
    * inline callbacks, for about 7 s. The other windows are shorter than a second each.
    */
   private static void soakWindow(ROS2Node publisherNode, ROS2Node subscriberNode) throws Exception
   {
      AtomicLong receivedCount = new AtomicLong();
      Runnable joints = inlinePair("soak_joints", publisherNode, subscriberNode, JointState.class, jointState(), new JointState(), receivedCount);
      Runnable image = inlinePair("soak_image", publisherNode, subscriberNode, Image.class, matrixImage(), new Image(), receivedCount);
      Runnable tick = new Runnable()
      {
         int ticks = 0;

         @Override
         public void run()
         {
            joints.run();
            if ((ticks++ % SOAK_IMAGE_EVERY) == 0)
               image.run();
         }
      };
      publisherWorker.runAndWait(tick, 2 * DEPTH * SOAK_IMAGE_EVERY, 0);
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(300));
      runPhase(tick, SOAK_WARMUP_TICKS, SOAK_TICK_NANOS, null, receivedCount);
      gatedWindow("soak-reliable", SOAK_TICKS + SOAK_TICKS / SOAK_IMAGE_EVERY, receivedCount, () -> runPhase(tick, SOAK_TICKS, SOAK_TICK_NANOS, null, receivedCount),
                  null);
   }

   /** Time the inline callback of the slow-reader window takes per sample. */
   private static final long SLOW_READER_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
   /** Shared-memory port queue capacity of the slow-reader window's subscriber participant. */
   private static final long SLOW_READER_PORT_QUEUE = 16;

   /**
    * A subscription that cannot keep up, as a GUI or a logger on the same host often cannot: its inline callback takes
    * {@value #SLOW_READER_NANOS} ns per sample while the sync publisher sends BEST_EFFORT JointState at ~25 kHz. With the
    * shared-memory transport the port queue ({@value #SLOW_READER_PORT_QUEUE} cells) of the subscription's participant (a
    * node of its own) fills up and the publishing thread discards samples for a full queue in steady state; with UDP the
    * socket buffers fill up; with intraprocess delivery the callback runs on the publishing thread.
    */
   private static void slowReaderWindow(ROS2Node publisherNode) throws Exception
   {
      ROS2NodeOptions slowOptions = ROS2NodeOptions.defaults();
      if ("udp".equals(System.getProperty(TRANSPORT_PROPERTY, "default")))
      {
         slowOptions.transports(TransportDescriptorTypeTools.createUDPv4Transport());
      }
      else
      {
         // A port queue much shorter than the sender's segment holds buffers: the queue fills up before the sender's
         // segment does (which is the 0004 overflow path, covered by the overflow windows).
         TransportDescriptorType sharedMemory = TransportDescriptorTypeTools.copyOf("shm".equals(System.getProperty(TRANSPORT_PROPERTY))
                                                                                   ? largeSegmentSharedMemory()
                                                                                   : TransportDescriptorTypeTools.createSHMDescriptor(), "nha_shm_slow");
         sharedMemory.setPortQueueCapacity(SLOW_READER_PORT_QUEUE);
         slowOptions.transports(sharedMemory, TransportDescriptorTypeTools.createUDPv4Transport());
      }
      ROS2Node slowNode = new ROS2Node("nha_slow", slowOptions);
      closeables.add(slowNode::close);
      AtomicLong receivedCount = new AtomicLong();
      JointState received = new JointState();
      ROS2QoSProfile qos = qos(ROS2QoSProfile.Reliability.BEST_EFFORT);
      ROS2Topic<JointState> topic = new ROS2Topic<>("/nha/slow_reader", JointState.class);
      ROS2Subscription<JointState> subscription = slowNode.createSubscription(topic, reader ->
      {
         if (reader.read(received))
            receivedCount.incrementAndGet();
         LockSupport.parkNanos(SLOW_READER_NANOS);
      }, qos);
      ROS2Publisher<JointState> publisher = publisherNode.createPublisher(topic, qos);
      closeables.add(() ->
                     {
                        publisherNode.destroyPublisher(publisher);
                        slowNode.destroySubscription(subscription);
                     });
      if (!publisher.waitForSubscription(10_000) || !subscription.waitForPublisher(10_000))
         throw new IllegalStateException("slow-reader: endpoints did not match");
      JointState message = jointState();
      Runnable publishOne = new Runnable()
      {
         int sequence = 0;

         @Override
         public void run()
         {
            stamp(message, ++sequence);
            publisher.publish(message);
         }
      };
      runPhase(publishOne, SMALL_WARMUP, SMALL_PACE_NANOS, null, receivedCount);
      gatedWindow("slow-reader", SMALL_MEASURED, receivedCount, () -> runPhase(publishOne, SMALL_MEASURED, SMALL_PACE_NANOS, null, receivedCount), null);
   }

   /** A RELIABLE publisher and a subscription with an inline callback on a new topic; returns the publish of one message. */
   private static <T extends ROS2Message<T>> Runnable inlinePair(String name, ROS2Node publisherNode, ROS2Node subscriberNode, Class<T> type, T message, T received,
                                                                 AtomicLong receivedCount)
   {
      ROS2QoSProfile qos = qos(ROS2QoSProfile.Reliability.RELIABLE);
      ROS2Topic<T> topic = new ROS2Topic<>("/nha/" + name, type);
      ROS2Subscription<T> subscription = subscriberNode.createSubscription(topic, reader ->
      {
         if (reader.read(received))
            receivedCount.incrementAndGet();
      }, qos);
      ROS2Publisher<T> publisher = publisherNode.createPublisher(topic, qos);
      closeables.add(() ->
                     {
                        publisherNode.destroyPublisher(publisher);
                        subscriberNode.destroySubscription(subscription);
                     });
      if (!publisher.waitForSubscription(10_000) || !subscription.waitForPublisher(10_000))
         throw new IllegalStateException(name + ": endpoints did not match");
      return new Runnable()
      {
         int sequence = 0;

         @Override
         public void run()
         {
            stamp(message, ++sequence);
            publisher.publish(message);
         }
      };
   }

   /** KEEP_LAST {@value #DEPTH}, VOLATILE, the given reliability: the QoS of every window. */
   private static ROS2QoSProfile qos(ROS2QoSProfile.Reliability reliability)
   {
      ROS2QoSProfile qos = new ROS2QoSProfile();
      qos.reliability(reliability);
      qos.history(ROS2QoSProfile.History.KEEP_LAST);
      qos.depth(DEPTH);
      qos.durability(ROS2QoSProfile.Durability.VOLATILE);
      return qos;
   }

   /**
    * No user traffic: what Fast-DDS does in the background (participant announcements, the shared-memory watchdog,
    * timers of the event thread). Gated like the other windows on the hot-path threads, i.e. every Fast-DDS thread and the
    * idle threads of this child: none of them may make a heap call in steady state. An attempt in which Fast-DDS processed
    * discovery data (e.g. another participant joining the domain) is measured again, so a periodic event that allocates
    * in discovery code (an announcement every 3 s is in every attempt) still fails the window.
    */
   private static void quietWindow()
   {
      LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
      gatedWindow("quiet-background", 0, null, () -> LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(QUIET_WINDOW_MILLIS)), null);
   }

   /* ------------------------------------------------------------------------------------------------------------ */

   /**
    * Calls every JNI entry point a running robot uses, directly, on hp-raw, with Fast-DDS entities created from
    * hand-written profiles that match what jros2 generates (RELIABLE, KEEP_LAST 10, PREALLOCATED_WITH_REALLOC, data
    * sharing off, two participants). Measures the native heap of every thread and the Java heap of hp-raw (which sees
    * Java objects created by JNI code: NewObject, NewDirectByteBuffer, NewStringUTF, exceptions...).
    */
   private static void rawJniWindow() throws Exception
   {
      String suffix = "_" + ProcessHandle.current().pid();
      String participantProfile = "nha_raw_participant" + suffix;
      String endpointProfile = "nha_raw_endpoint" + suffix;
      String topicProfile = "nha_raw_topic" + suffix;
      String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dds xmlns=\"" + ProfilesXML.FAST_DDS_NAMESPACE_URI + "\"><profiles>"
                   + "<participant profile_name=\"" + participantProfile + "\"><domainId>" + domainId
                   + "</domainId><rtps><useBuiltinTransports>true</useBuiltinTransports></rtps></participant>"
                   + "<topic profile_name=\"" + topicProfile + "\"/>"
                   + rawEndpoint("data_writer", endpointProfile, "<publishMode><kind>SYNCHRONOUS</kind></publishMode>")
                   + rawEndpoint("data_reader", endpointProfile, "")
                   + "</profiles><library_settings><intraprocess_delivery>" + (intraprocess() ? "USER_DATA_ONLY" : "OFF")
                   + "</intraprocess_delivery></library_settings></dds>";
      synchronized (ProfilesXML.getLoadLock())
      {
         fastddsjavaTools.retcodeThrowOnError(fastddsjava.loadXmlProfilesString(xml));
      }

      String typeName = "nha_raw_type" + suffix;
      String topicName = "nha_raw_topic" + suffix;
      long writerParticipant = fastddsjava.createParticipant(participantProfile);
      long readerParticipant = fastddsjava.createParticipant(participantProfile);
      long[] types = new long[2];
      long[] typeSupports = new long[2];
      long[] topics = new long[2];
      long[] participants = {writerParticipant, readerParticipant};
      for (int i = 0; i < 2; ++i)
      {
         types[i] = fastddsjava.createTopicDataWrapperType(typeName, fastddsjava.CDR_LE);
         typeSupports[i] = fastddsjava.createTypesupport(types[i]);
         fastddsjavaTools.retcodeThrowOnError(fastddsjava.registerType(participants[i], typeSupports[i]));
         topics[i] = fastddsjava.createTopic(participants[i], types[i], topicName, topicProfile);
      }
      long publisher = fastddsjava.createPublisher(writerParticipant, endpointProfile);
      long writer = fastddsjava.createDataWriter(publisher, topics[0], endpointProfile);
      AtomicLong callbacks = new AtomicLong();
      fastddsjavaCallback onData = callbacks::incrementAndGet;
      long readerListener = fastddsjava.createDataReaderListener();
      fastddsjava.dataReaderListenerSetOnDataAvailable(readerListener, onData);
      long subscriber = fastddsjava.createSubscriber(readerParticipant, endpointProfile);
      long reader = fastddsjava.createDataReader(subscriber, topics[1], readerListener, endpointProfile);
      long writeData = fastddsjava.createData(types[0]);
      long readData = fastddsjava.createData(types[1]);
      long sampleInfo = fastddsjava.createSampleInfo();
      long deadline = System.currentTimeMillis() + 10_000;
      while ((fastddsjava.dataWriterGetPublicationMatchedCount(writer) == 0 || fastddsjava.dataReaderGetSubscriptionMatchedCount(reader) == 0)
             && System.currentTimeMillis() < deadline)
         Thread.sleep(10);

      final int payloadSize = 256;
      ByteBuffer source = ByteBuffer.allocateDirect(payloadSize).order(ByteOrder.LITTLE_ENDIAN);
      source.put(0, (byte) 0).put(1, (byte) 1);
      ByteBuffer destination = ByteBuffer.allocateDirect(4096);
      byte[] heapBytes = new byte[payloadSize];
      byte[] guid = new byte[16];
      long[] sink = new long[1];
      fastddsjava.topicDataResize(writeData, payloadSize);

      Runnable iteration = () ->
      {
         long acc = 0;
         // ABI v2 write + take
         acc += fastddsjava.dataWriterWriteBuffer(writer, source, payloadSize);
         // ABI v1 write path
         fastddsjava.topicDataResize(writeData, payloadSize);
         fastddsjava.topicDataWriteBuffer(writeData, source, 0, payloadSize);
         fastddsjava.topicDataWrite(writeData, heapBytes, 0, payloadSize);
         acc += fastddsjava.dataWriterWrite(writer, writeData);
         // Wait (spinning, bounded) for the two samples so that takes find data
         long waitUntil = System.nanoTime() + 5_000_000L;
         while (fastddsjava.dataReaderGetUnreadCount(reader) < 2 && System.nanoTime() < waitUntil)
            Thread.onSpinWait();
         acc += fastddsjava.dataReaderReadNextSample(reader, readData, sampleInfo);
         int taken = fastddsjava.dataReaderTakeNextToBuffer(reader, readData, sampleInfo, destination, destination.capacity());
         acc += taken;
         acc += fastddsjava.dataReaderTakeNextCustom(reader, readData, sampleInfo);
         acc += fastddsjava.topicDataSize(readData);
         fastddsjava.topicDataReadBuffer(readData, destination, 0, payloadSize);
         fastddsjava.topicDataRead(readData, heapBytes, 0, payloadSize);
         acc += fastddsjava.sampleInfoValidData(sampleInfo) ? 1 : 0;
         acc += fastddsjava.sampleInfoSourceTimestampNanos(sampleInfo);
         acc += fastddsjava.sampleInfoReceptionTimestampNanos(sampleInfo);
         acc += fastddsjava.sampleInfoSampleState(sampleInfo) + fastddsjava.sampleInfoViewState(sampleInfo) + fastddsjava.sampleInfoInstanceState(sampleInfo);
         acc += fastddsjava.dataReaderGetUnreadCount(reader);
         acc += fastddsjava.dataWriterGetPublicationMatchedCount(writer);
         acc += fastddsjava.dataReaderGetSubscriptionMatchedCount(reader);
         acc += fastddsjava.dataReaderGetSubscriptionMatchedStatus(reader, guid);
         fastddsjava.getWriterGuid(writer, guid);
         fastddsjava.getReaderGuid(reader, guid);
         fastddsjava.getParticipantGuid(writerParticipant, guid);
         acc += fastddsjava.abiVersion();
         // Drain anything left
         while (fastddsjava.dataReaderTakeNextToBuffer(reader, readData, 0, destination, destination.capacity()) >= 0)
            acc++;
         sink[0] += acc;
      };

      Worker raw = new Worker("hp-raw");
      int warmup = 20000;
      int measured = 3000;
      raw.runAndWait(iteration, warmup, 0);
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
      gatedWindow("raw-jni", measured * 2L, callbacks, () ->
      {
         raw.runAndWait(iteration, measured, 0);
         LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(200));
      }, raw::lastJavaBytes);

      fastddsjava.dataReaderSetListener(reader, 0);
      fastddsjava.deleteDataReader(subscriber, reader);
      fastddsjava.deleteDataReaderListener(readerListener);
      fastddsjava.deleteDataWriter(publisher, writer);
      fastddsjava.deleteSubscriber(readerParticipant, subscriber);
      fastddsjava.deletePublisher(writerParticipant, publisher);
      fastddsjava.deleteData(types[0], writeData);
      fastddsjava.deleteData(types[1], readData);
      fastddsjava.deleteSampleInfo(sampleInfo);
      for (int i = 0; i < 2; ++i)
      {
         fastddsjava.deleteTopic(participants[i], topics[i]);
         fastddsjava.unregisterType(participants[i], typeName);
         fastddsjava.deleteTypesupport(typeSupports[i]);
         fastddsjava.deleteParticipant(participants[i]);
      }
   }

   /** The intraprocess delivery mode the parent selected for this child (the raw window configures Fast-DDS itself). */
   private static boolean intraprocess()
   {
      return Boolean.parseBoolean(System.getenv("FASTDDS_INTRAPROCESS_DELIVERY"));
   }

   private static String rawEndpoint(String element, String profile, String extra)
   {
      return "<" + element + " profile_name=\"" + profile + "\"><topic><historyQos><kind>KEEP_LAST</kind><depth>" + DEPTH + "</depth></historyQos></topic>"
             + "<qos><durability><kind>VOLATILE</kind></durability><reliability><kind>RELIABLE</kind></reliability>"
             + "<data_sharing><kind>OFF</kind></data_sharing>" + extra + "</qos>"
             + "<historyMemoryPolicy>PREALLOCATED_WITH_REALLOC</historyMemoryPolicy></" + element + ">";
   }

   /* ------------------------------------------------------------------------------------------------------------ */

   /** Totals of one armed window. */
   static final class Summary
   {
      long library;
      long jvm;
      /** JIT compiler activity on hot-path threads (reported, see {@link #category}). */
      long jit;
      /** Handshakes / safepoint operations that other threads requested (reported, see {@link #category}). */
      long handshake;
      /** HotSpot monitor inflation on hot-path threads (gated, see {@link #category}). */
      long monitor;
      /** Fast-DDS discovery processing on hot-path threads (see {@link #category} and gatedWindow). */
      long discovery;
      /** JVM / JDK native methods called by Java code on hot-path threads (gated). */
      long javaNative;
      /** Garbage collector buffers on hot-path threads (purely JVM-internal: reported). */
      long gc;
      long background;
      /** Calls on Fast-DDS threads (reported for the quiet window). */
      long fastddsThreadCalls;
      /** Java heap bytes of the callback threads Fast-DDS attached ("fastdds-callback"). */
      long callbackThreadJavaBytes;
   }

   /**
    * Measures {@code phase} in an armed window, again (up to {@value #MAX_ATTEMPTS} attempts) only while an attempt was
    * disturbed by Fast-DDS discovery processing, and prints the RESULT line of the window (see the class documentation:
    * every non-discovery call of every attempt counts).
    *
    * @param received          received-message counter (null if not tracked); every attempt must receive messages
    * @param callerJavaBytes   Java heap bytes the JNI calls made on the calling thread in the last run of phase, or null
    *                          when the window does not gate JNI-created Java objects
    */
   private static void gatedWindow(String name, long sent, AtomicLong received, Runnable phase, LongSupplier callerJavaBytes)
   {
      long libraryCalls = 0;
      long javaNativeCalls = 0;
      long jvmInternalCalls = 0;
      long jitCalls = 0;
      long gcCalls = 0;
      long handshakeCalls = 0;
      long monitorCalls = 0;
      long discoveryCalls = 0;
      int disturbedAttempts = 0;
      boolean disturbed = false;
      long background = 0;
      long jniJavaBytes = callerJavaBytes == null ? -1 : 0;
      long dropped = 0;
      long unslotted = 0;
      long shimInternal = 0;
      Summary last = null;
      int attempt = 0;
      while (attempt < MAX_ATTEMPTS)
      {
         attempt++;
         long receivedBefore = received == null ? 0 : received.get();
         long[] javaBefore = javaBytes.snapshot();
         counter.arm();
         phase.run();
         NativeAllocationCounter.Window window = counter.disarmAndRead();
         Map<String, Long> javaDiff = javaBytes.diff(javaBefore, javaBytes.snapshot());
         last = report(name + "#" + attempt, window, sent, received == null ? -1 : received.get() - receivedBefore, javaDiff);
         // Every attempt counts in full; only discovery calls are not counted (see below).
         libraryCalls += last.library;
         javaNativeCalls += last.javaNative;
         jvmInternalCalls += last.jvm;
         monitorCalls += last.monitor;
         jitCalls += last.jit;
         gcCalls += last.gc;
         handshakeCalls += last.handshake;
         discoveryCalls += last.discovery;
         background += last.background;
         dropped += window.droppedThreads;
         unslotted += window.unslottedCalls;
         shimInternal += window.internalCalls;
         if (callerJavaBytes != null)
            jniJavaBytes += Math.max(0, callerJavaBytes.getAsLong()) + last.callbackThreadJavaBytes;
         // An attempt in which Fast-DDS processed discovery data (a participant or endpoint that joined, left or changed,
         // e.g. one created just before this window, or another process on the domain) is disturbed: its discovery calls
         // are not the hot path, and the window is measured again. This is the only reason for another attempt.
         disturbed = last.discovery > 0;
         if (!disturbed)
            break;
         disturbedAttempts++;
      }
      if (disturbed)
      {
         // No undisturbed attempt: the discovery calls of the last attempt count as library calls.
         libraryCalls += last.discovery;
      }
      long hotPathCalls = libraryCalls;
      System.out.println(PREFIX + "RESULT " + name + " gated=true hotPathCalls=" + hotPathCalls + " javaNativeCalls=" + javaNativeCalls + " hotPathJvmInternalCalls="
                         + jvmInternalCalls + " monitorInflationCalls=" + monitorCalls + " attempts=" + attempt + " disturbedAttempts=" + disturbedAttempts
                         + " discoveryCalls=" + discoveryCalls + " jitCalls=" + jitCalls + " gcCalls=" + gcCalls + " handshakeCalls=" + handshakeCalls
                         + " backgroundCalls=" + background + " jniJavaBytes=" + jniJavaBytes + " droppedThreads=" + dropped + " unslottedCalls=" + unslotted
                         + " shimInternalCalls=" + shimInternal);
      System.out.flush();
      if (hotPathCalls != 0 || javaNativeCalls != 0 || monitorCalls != 0 || jvmInternalCalls != 0 || dropped != 0 || unslotted != 0 || shimInternal != 0
          || jniJavaBytes > 0)
         anyFailure = true;
   }

   /** Prints the WINDOW, THREAD, STACK and JAVA lines of one armed window and returns its totals. */
   private static Summary report(String windowName, NativeAllocationCounter.Window window, long sent, long received, Map<String, Long> javaDiff)
   {
      return report(windowName, window, sent, received, javaDiff, NativeHeapAllocationChild::classify);
   }

   /**
    * Prints the WINDOW, THREAD, STACK and JAVA lines of one armed window and returns its totals.
    *
    * @param classifier thread name (as the kernel knows it) to "hot-java", "fastdds" or "jvm" (background)
    */
   static Summary report(String windowName,
                         NativeAllocationCounter.Window window,
                         long sent,
                         long received,
                         Map<String, Long> javaDiff,
                         java.util.function.Function<String, String> classifier)
   {
      NativeStackResolver resolver;
      try
      {
         resolver = new NativeStackResolver();
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
      System.out.println(PREFIX + "WINDOW " + windowName + " sent=" + sent + " received=" + received + " totalCalls=" + window.totalCalls + " droppedThreads="
                         + window.droppedThreads + " unslottedCalls=" + window.unslottedCalls + " shimInternalCalls=" + window.internalCalls);
      Summary summary = new Summary();
      for (NativeAllocationCounter.ThreadCounts thread : window.threads)
      {
         if (thread.calls() == 0)
            continue;
         String name = currentName(thread.tid, thread.name);
         String threadClass = classifier.apply(name);
         System.out.println(PREFIX + "THREAD " + windowName + " " + threadClass + " " + thread.tid + " " + name.replace(' ', '_') + " " + thread.summary());
         long libraryCalls = 0;
         long javaNativeCalls = 0;
         long jvmCalls = 0;
         long jitCalls = 0;
         long gcCalls = 0;
         long handshakeCalls = 0;
         long monitorCalls = 0;
         long discoveryCalls = 0;
         List<NativeAllocationCounter.Stack> all = new ArrayList<>(thread.allocationStacks);
         all.addAll(thread.freeStacks);
         for (NativeAllocationCounter.Stack stack : all)
         {
            List<NativeStackResolver.Frame> frames = resolver.resolve(stack.frames);
            String category = category(frames);
            switch (category)
            {
               case "library" -> libraryCalls += stack.count;
               case "java-native" -> javaNativeCalls += stack.count;
               case "jvm-jit" -> jitCalls += stack.count;
               case "jvm-gc" -> gcCalls += stack.count;
               case "jvm-handshake" -> handshakeCalls += stack.count;
               case "jvm-monitor" -> monitorCalls += stack.count;
               case "discovery" -> discoveryCalls += stack.count;
               default -> jvmCalls += stack.count;
            }
            StringBuilder line = new StringBuilder(PREFIX + "STACK " + windowName + " " + thread.tid + " " + name.replace(' ', '_') + " "
                                                   + NativeAllocationCounter.kindName(stack.kind) + " size=" + stack.size + " count=" + stack.count + " "
                                                   + category);
            for (NativeStackResolver.Frame frame : frames)
               line.append(" | ").append(frame);
            System.out.println(line);
         }
         // Calls whose stack did not fit the table: attributed conservatively to the library if any stack of the thread was,
         // to discovery if the thread's recorded stacks were all discovery processing (a burst of it, e.g. a participant
         // joining, overflows the table; the attempt is disturbed and measured again), and to the gated JVM-internal
         // category otherwise.
         long unrecorded = thread.allocationUnrecorded + thread.freeUnrecorded;
         if (libraryCalls > 0)
            libraryCalls += unrecorded;
         else if (discoveryCalls > 0 && javaNativeCalls == 0 && jvmCalls == 0 && monitorCalls == 0)
            discoveryCalls += unrecorded;
         else
            jvmCalls += unrecorded;
         // A thread that processed discovery data in this window (a participant or endpoint joining or leaving) also does work
         // for it that its stacks cannot attribute: e.g. the SPDP receive thread records the new participant's writer in
         // BaseReader::update_last_notified before any discovery listener frame is on the stack. The library calls of such a
         // thread count as discovery (this attempt is disturbed and measured again); every other thread is unaffected, and
         // the undisturbed attempt that the window needs is gated in full.
         if (discoveryCalls > 0 && libraryCalls > 0)
         {
            System.out.println(PREFIX + "FALLOUT " + windowName + " " + thread.tid + " " + name.replace(' ', '_') + " libraryCallsCountedAsDiscovery=" + libraryCalls);
            discoveryCalls += libraryCalls;
            libraryCalls = 0;
         }
         long threadCalls = libraryCalls + javaNativeCalls + jvmCalls + jitCalls + gcCalls + handshakeCalls + monitorCalls + discoveryCalls;
         if (threadClass.equals("jvm"))
         {
            summary.background += threadCalls;
         }
         else
         {
            summary.library += libraryCalls;
            summary.javaNative += javaNativeCalls;
            summary.jvm += jvmCalls;
            summary.jit += jitCalls;
            summary.gc += gcCalls;
            summary.handshake += handshakeCalls;
            summary.monitor += monitorCalls;
            summary.discovery += discoveryCalls;
            if (threadClass.equals("fastdds"))
               summary.fastddsThreadCalls += threadCalls;
         }
      }
      for (Map.Entry<String, Long> entry : javaDiff.entrySet())
      {
         if (entry.getValue() != 0)
            System.out.println(PREFIX + "JAVA " + windowName + " " + entry.getKey().replace(' ', '_') + " bytes=" + entry.getValue());
         if (entry.getKey().startsWith("fastdds-callback"))
            summary.callbackThreadJavaBytes += Math.max(0, entry.getValue());
      }
      System.out.flush();
      return summary;
   }

   /**
    * hot-java: the threads this child drives; fastdds: every thread Fast-DDS creates ("dds.udp.*", "dds.shm.*",
    * "dds.ev.*", "dds.shm.wdog", "dds.asyn.*", "dds.dsha.*", "dds.log", ...), also when attached to the JVM; jvm: the rest.
    */
   static String classify(String threadName)
   {
      if (threadName.startsWith("hp-"))
         return "hot-java";
      if (threadName.startsWith("dds.") || threadName.startsWith("fastdds-callback"))
         return "fastdds";
      return "jvm";
   }

   /**
    * Frames of Fast-DDS discovery processing new or changed data of a participant or endpoint (not of the user's hot path:
    * creating, deleting or changing entities). These frames also cover the periodic participant announcements
    * (PDP::announceParticipantState, every 3 s) and the processing of the announcements of known participants, which are
    * steady state: an attempt with such calls is measured again (see gatedWindow), so an allocation there is excused in
    * the short windows, but the 7 s windows (quiet-background, soak-reliable) contain at least two announcements in every
    * attempt and fail on it. Liveliness (WLP) frames are not in this list. Also the shared-memory watchdog's registration
    * of a segment of a participant that joined or left (asynchronous, up to a second after the participant was created or
    * deleted, e.g. the participants of the raw JNI window and of the slow-reader window), and the event thread's timer
    * collection growing for the timed events of newly matched participants or endpoints (ResourceEvent::event_service
    * reserving its std::vector of TimedEventImpl*).
    */
   private static final String[] DISCOVERY_FRAMES = {"PDPListener::", "PDPSimple::", "PDPServer", "PDPClient", "PDP::", "EDPSimplePUBListener::",
                                                     "EDPSimpleSUBListener::", "EDPBasePUBListener::", "EDPBaseSUBListener::", "EDP::", "EDPSimple::",
                                                     "ParticipantProxyData::", "ReaderProxyData::", "WriterProxyData::",
                                                     // The shared-memory watchdog adding / removing the segment of a participant
                                                     // that this one started or stopped exchanging data with (only then: its
                                                     // to_add_ / to_remove_ lists are empty in steady state), up to one
                                                     // watchdog period (1 s) after that participant was created or deleted
                                                     "SegmentWrapper::WatchTask::update_watched_segments",
                                                     // The event thread's timer collection growing: only when timed events are
                                                     // registered that never existed at once before, i.e. for newly matched
                                                     // remote participants / endpoints (or new local entities); never with a
                                                     // fixed set of matched entities
                                                     "std::vector<eprosima::fastdds::rtps::TimedEventImpl*"};

   /** Frames of the JIT: compile requests, on-stack replacement, deoptimization and call site (inline cache) resolution. */
   private static final String[] JIT_FRAMES = {"CompileBroker::", "CompilationPolicy::", "CompileTask::", "Deoptimization::", "vframeArray",
                                               "SharedRuntime::OSR_migration", "SharedRuntime::resolve", "SharedRuntime::handle_wrong_method",
                                               "SharedRuntime::fixup_callers_callsite", "CompiledIC::", "CompiledMethod::", "nmethod::",
                                               "Runtime1::counter_overflow", "InterpreterRuntime::frequency_counter_overflow",
                                               "ConstantPool::resolve_string_constants", "AdapterHandlerLibrary::"};

   /** Frames of the garbage collector: barrier buffers (G1 dirty card / SATB queues) and other collector bookkeeping. */
   private static final String[] GC_FRAMES = {"G1", "BufferNode::", "PtrQueue", "SATBMarkQueue", "DirtyCardQueue", "CardTable", "BarrierSet",
                                              "CollectedHeap::", "ParallelScavenge", "PSPromotion", "Shenandoah", "ZBarrier", "ZPage", "SerialHeap"};

   /** C/C++ runtime libraries: not the caller of a heap call, whoever called them is. */
   private static boolean isRuntimeLibrary(String library)
   {
      return library.startsWith("liballoccount") || library.startsWith("libc.so") || library.startsWith("libc-") || library.startsWith("libstdc++")
             || library.startsWith("libgcc_s") || library.startsWith("ld-linux") || library.startsWith("libm.so") || library.startsWith("libpthread");
   }

   /** The JNI layer and Fast-DDS. */
   private static boolean isLibrary(String library)
   {
      return library.startsWith("libjnifastddsjava") || library.startsWith("libfastdds") || library.startsWith("libfastcdr");
   }

   /**
    * Who made the call (frames innermost first). Skipping the shim and the C/C++ runtime (libc, libstdc++, libgcc, the
    * dynamic loader), the innermost caller decides:
    * <ul>
    *    <li>the JNI layer or Fast-DDS: {@code library} ({@code discovery} if a Fast-DDS discovery frame is on the stack);</li>
    *    <li>the JVM: following the stack outwards over the JVM's frames, the first other frame tells who called the JVM. The
    *    JNI layer or Fast-DDS (a JNI function or AttachCurrentThread called by the library code): {@code library}. A JDK
    *    native library (libjava, libnio, ...), or a JVM entry point that Java code calls ({@code Unsafe_*}, {@code JVM_*}):
    *    {@code java-native}. Otherwise (JIT-compiled or interpreted Java code, nothing) the JVM works for the Java code
    *    that runs: {@code jvm-monitor} for monitor inflation (not when a deoptimization re-locks the monitors of the frames
    *    it rebuilds), {@code jvm-jit} for the JIT compiler at work (a compile request, on-stack replacement, a
    *    deoptimization, an inline cache resolution: warm-up and re-profiling), {@code jvm-handshake} for a handshake /
    *    safepoint operation another thread requested, {@code jvm-gc} for garbage collector barrier buffers, and
    *    {@code jvm-internal} otherwise (also when the recorded stack ends inside the JVM);</li>
    *    <li>JDK native code calling the C library directly: {@code java-native}; compiled code calling the C library
    *    directly with nothing else on the stack (a leaf runtime call of the JIT whose libjvm frames were tail calls, e.g.
    *    SharedRuntime::OSR_migration_end freeing the buffer of OSR_migration_begin): {@code jvm-jit}.</li>
    * </ul>
    * Gated: library, java-native, jvm-monitor, jvm-internal (and discovery when no attempt was undisturbed). Reported:
    * jvm-jit, jvm-handshake, jvm-gc.
    */
   static String category(List<NativeStackResolver.Frame> frames)
   {
      int first = -1;
      int callers = 0;
      for (int i = 0; i < frames.size(); ++i)
      {
         if (isRuntimeLibrary(frames.get(i).library))
            continue;
         callers++;
         if (first < 0)
            first = i;
      }
      if (first < 0)
         return "jvm-internal";
      String caller = frames.get(first).library;
      if (isLibrary(caller))
         return libraryOrDiscovery(frames);
      if (caller.startsWith("libjvm"))
      {
         boolean javaEntryPoint = false;
         String outerCaller = null;
         for (int i = first; i < frames.size(); ++i)
         {
            NativeStackResolver.Frame frame = frames.get(i);
            if (frame.library.startsWith("libjvm"))
            {
               javaEntryPoint |= frame.function.startsWith("Unsafe_") || frame.function.startsWith("JVM_");
               continue;
            }
            if (isRuntimeLibrary(frame.library))
               continue;
            outerCaller = frame.library;
            break;
         }
         if (outerCaller != null && isLibrary(outerCaller))
            return libraryOrDiscovery(frames);
         String jvm = jvmCategory(frames);
         // Monitor inflation (e.g. Object.hashCode / notify through JVM_*) and handshakes processed on the way stay what they are
         if (jvm.equals("jvm-monitor") || jvm.equals("jvm-handshake"))
            return jvm;
         if (javaEntryPoint || (outerCaller != null && !outerCaller.equals("[anon]")))
            return "java-native";
         return jvm;
      }
      if (caller.equals("[anon]"))
         return callers == 1 ? "jvm-jit" : jvmCategory(frames);
      // A JDK native library (libjava, libnio, libnet, libzip, ...) called the C library itself
      return "java-native";
   }

   private static String libraryOrDiscovery(List<NativeStackResolver.Frame> frames)
   {
      for (NativeStackResolver.Frame frame : frames)
      {
         if (!frame.library.startsWith("libfastdds"))
            continue;
         for (String discoveryFrame : DISCOVERY_FRAMES)
         {
            if (frame.function.contains(discoveryFrame))
               return "discovery";
         }
      }
      return "library";
   }

   /** The category of JVM work for the Java code that runs (see {@link #category}). */
   private static String jvmCategory(List<NativeStackResolver.Frame> frames)
   {
      boolean inflation = false;
      boolean deoptimization = false;
      for (NativeStackResolver.Frame frame : frames)
      {
         inflation |= frame.library.startsWith("libjvm") && frame.function.contains("ObjectSynchronizer::inflate");
         deoptimization |= frame.library.startsWith("libjvm") && frame.function.contains("Deoptimization::");
      }
      if (inflation && !deoptimization)
         return "jvm-monitor";
      for (NativeStackResolver.Frame frame : frames)
      {
         if (!frame.library.startsWith("libjvm"))
            continue;
         for (String jitFrame : JIT_FRAMES)
         {
            if (frame.function.contains(jitFrame))
               return "jvm-jit";
         }
         if (frame.function.contains("HandshakeState::process_by_self") || frame.function.contains("SafepointSynchronize::block"))
            return "jvm-handshake";
      }
      for (NativeStackResolver.Frame frame : frames)
      {
         if (!frame.library.startsWith("libjvm"))
            continue;
         for (String gcFrame : GC_FRAMES)
         {
            if (frame.function.contains(gcFrame))
               return "jvm-gc";
         }
      }
      return "jvm-internal";
   }

   private static String currentName(int tid, String nameAtFirstCall)
   {
      try
      {
         String comm = new String(Files.readAllBytes(Paths.get("/proc/self/task/" + tid + "/comm")), StandardCharsets.UTF_8).trim();
         if (!comm.isEmpty())
            return comm;
      }
      catch (Exception ignored)
      {
         // the thread ended
      }
      return nameAtFirstCall.isEmpty() ? "?" : nameAtFirstCall;
   }

   private static long currentTid()
   {
      try
      {
         return Long.parseLong(Paths.get("/proc/thread-self").toRealPath().getFileName().toString());
      }
      catch (Exception e)
      {
         return -1;
      }
   }

   /* ------------------------------------------------------------------------------------------------------------ */

   private static JointState jointState()
   {
      JointState message = new JointState();
      message.getHeader().setFrameId("base_link");
      for (int i = 0; i < JOINTS; ++i)
      {
         message.getName().add(String.format(Locale.ROOT, "robot_joint_%02d", i));
         message.getPosition().add(0.1 * i);
         message.getVelocity().add(0.01 * i);
         message.getEffort().add(1.5 * i);
      }
      return message;
   }

   /** The large message of the matrix: a 2 MB image. */
   private static Image matrixImage()
   {
      return image(IMAGE_WIDTH, IMAGE_HEIGHT);
   }

   private static Image image(int width, int height)
   {
      Image message = new Image();
      message.getHeader().setFrameId("camera_optical_frame");
      message.setHeight(height);
      message.setWidth(width);
      message.setEncoding("mono8");
      message.setStep(width);
      for (int i = 0; i < width * height; ++i)
         message.getData().add((byte) i);
      return message;
   }

   private static void stamp(ROS2Message<?> message, int sequence)
   {
      if (message instanceof JointState jointState)
      {
         jointState.getHeader().getStamp().setSec(sequence);
         jointState.getPosition().putAt(0, sequence);
      }
      else if (message instanceof Image image)
      {
         image.getHeader().getStamp().setSec(sequence);
         image.getData().putAt(0, (byte) sequence);
      }
   }

   /* ------------------------------------------------------------------------------------------------------------ */

   /**
    * A named, reusable thread: the native thread name stays stable across windows. The repetition loop of a task runs
    * in the worker's own long-lived loop, so the harness does not enter a hot loop through the interpreter (and on-stack
    * replacement, which allocates) at every window.
    */
   static final class Worker
   {
      private static final com.sun.management.ThreadMXBean BEAN = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

      private final Thread thread;
      private volatile Runnable task;
      private volatile BooleanSupplier repeatWhile;
      private volatile int times;
      private volatile long paceNanos;
      private volatile boolean stop;
      private volatile boolean busy;
      /** Java heap bytes the worker allocated while running the last task (measured on the worker thread). */
      private volatile long lastJavaBytes;

      Worker(String name)
      {
         thread = new Thread(this::loop, name);
         thread.setDaemon(true);
         thread.start();
      }

      private void loop()
      {
         while (true)
         {
            Runnable current = task;
            if (current == null)
            {
               LockSupport.park(this);
               continue;
            }
            try
            {
               long before = BEAN.getCurrentThreadAllocatedBytes();
               BooleanSupplier condition = repeatWhile;
               int count = times;
               long pace = paceNanos;
               for (int i = 0; i < count && !stop; ++i)
               {
                  if (condition != null && !condition.getAsBoolean())
                  {
                     if (pace > 0)
                        LockSupport.parkNanos(pace);
                     continue;
                  }
                  current.run();
                  if (pace > 0)
                     LockSupport.parkNanos(pace);
               }
               lastJavaBytes = BEAN.getCurrentThreadAllocatedBytes() - before;
            }
            catch (Throwable t)
            {
               t.printStackTrace(System.out);
            }
            task = null;
            busy = false;
         }
      }

      /** Runs {@code runnable} {@code count} times on the worker, {@code pace} nanoseconds apart, without waiting. */
      void start(Runnable runnable, int count, long pace)
      {
         start(runnable, null, count, pace);
      }

      /**
       * Like {@link #start(Runnable, int, long)}, but a repetition is skipped (only paced) while {@code condition} is
       * false, and the task stops early at {@link #stop()}.
       */
      void start(Runnable runnable, BooleanSupplier condition, int count, long pace)
      {
         awaitIdle();
         busy = true;
         stop = false;
         repeatWhile = condition;
         times = count;
         paceNanos = pace;
         task = runnable;
         LockSupport.unpark(thread);
      }

      void stop()
      {
         stop = true;
      }

      void awaitIdle()
      {
         while (busy)
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
      }

      void runAndWait(Runnable runnable)
      {
         runAndWait(runnable, 1, 0);
      }

      void runAndWait(Runnable runnable, int count, long pace)
      {
         start(runnable, count, pace);
         awaitIdle();
      }

      long lastJavaBytes()
      {
         return lastJavaBytes;
      }
   }

   /** Per-thread Java heap bytes (HotSpot), read from the observer (the calling) thread. */
   static final class ThreadMXBeanAccess
   {
      private final com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
      private long[] ids = new long[0];
      private String[] names = new String[0];

      long currentThreadAllocatedBytes()
      {
         return bean.getCurrentThreadAllocatedBytes();
      }

      /**
       * Snapshot of the allocated bytes of all threads. Uses the thread ids of the ThreadMXBean, not
       * Thread.getAllStackTraces(): a stack walk is a handshake with every thread, which those threads then process (and
       * free) at their next safepoint poll, possibly inside the measured window.
       */
      long[] snapshot()
      {
         long[] current = bean.getAllThreadIds();
         if (!Arrays.equals(current, ids))
         {
            ids = current;
            names = new String[ids.length];
            java.lang.management.ThreadInfo[] infos = bean.getThreadInfo(ids, 0);
            for (int i = 0; i < ids.length; ++i)
               names[i] = infos[i] == null ? "?" : infos[i].getThreadName();
         }
         long[] values = bean.getThreadAllocatedBytes(ids);
         long[] result = new long[ids.length * 2];
         for (int i = 0; i < ids.length; ++i)
         {
            result[2 * i] = ids[i];
            result[2 * i + 1] = values[i];
         }
         return result;
      }

      /** Java bytes per hot-path thread (hp-* and attached Fast-DDS threads) between two snapshots. */
      Map<String, Long> diff(long[] before, long[] after)
      {
         Map<Long, Long> beforeById = new HashMap<>();
         for (int i = 0; i < before.length; i += 2)
            beforeById.put(before[i], before[i + 1]);
         Map<Long, String> namesById = new HashMap<>();
         for (int i = 0; i < ids.length; ++i)
            namesById.put(ids[i], names[i]);
         Map<String, Long> result = new HashMap<>();
         for (int i = 0; i < after.length; i += 2)
         {
            Long start = beforeById.get(after[i]);
            String name = namesById.getOrDefault(after[i], "?");
            if (start == null || after[i + 1] < 0 || !(name.startsWith("hp-") || name.startsWith("fastdds-callback")))
               continue;
            result.merge(name + "#" + after[i], after[i + 1] - start, Long::sum);
         }
         return result;
      }
   }
}
