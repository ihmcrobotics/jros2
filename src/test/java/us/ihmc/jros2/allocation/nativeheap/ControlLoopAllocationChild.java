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

import us.ihmc.fastddsjava.cdr.idl.IDLByteSequence;
import us.ihmc.jros2.AsyncROS2Node;
import us.ihmc.jros2.AsyncROS2Publisher;
import us.ihmc.jros2.ROS2CallbackExecutor;
import us.ihmc.jros2.ROS2Context;
import us.ihmc.jros2.ROS2Node;
import us.ihmc.jros2.ROS2NodeOptions;
import us.ihmc.jros2.ROS2Publisher;
import us.ihmc.jros2.ROS2QoSProfile;
import us.ihmc.jros2.ROS2Subscription;
import us.ihmc.jros2.ROS2Topic;
import us.ihmc.jros2.Statistics;
import us.ihmc.jros2.allocation.AllocationGate;
import us.ihmc.jros2.allocation.SpinWait;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * Child JVM of {@link ControlLoopAllocationTest}: a realistic robot process, measured for at least 10 s of steady state
 * with the Java heap gate ({@link AllocationGate}: every thread) and the native heap counter (the alloccount LD_PRELOAD
 * shim: every hot-path thread) in the SAME windows.
 * <p>
 * Process layout: one {@link ROS2Context} (one participant) with three nodes, plus one node with its own participant.
 * <ul>
 *    <li>"controller" (context): publishes sensor_msgs/JointState (30 joints) synchronously at 1 kHz on hp-control, the
 *    control thread, which sleeps until the next period between ticks; polls the estimate with readLatest every tick and
 *    makes per-tick queries (unread count, publisher / subscription statistics, async queue state);</li>
 *    <li>"estimator" (context, {@link ROS2CallbackExecutor} "hp-exec"): receives every JointState on the executor and
 *    publishes a geometry_msgs/PoseStamped estimate from the callback;</li>
 *    <li>"camera" (context, {@link AsyncROS2Node} with publish thread hp-async-pub): publishes a 640x512 rgb8
 *    sensor_msgs/Image (983 kB, fragmented) RELIABLE at 30 Hz, from the control thread through its queue;</li>
 *    <li>"monitor" (own participant): receives JointState and Image with inline callbacks on Fast-DDS threads.</li>
 * </ul>
 * Statistics are enabled on every publisher and subscription. Intraprocess delivery comes from the environment
 * (OFF or FULL), the native ABI is v2. jros2's default transports.
 * <p>
 * The gate's instrumenter pass (agent JVM) and thread-allocated-bytes pass (all JVMs) each run {@value #ITERATIONS} ticks
 * (10 s); the native counter is armed inside both windows, from the first tick to the end of the settle step (so it
 * covers exactly what the Java passes measure). Native calls are attributed like in {@link NativeHeapAllocationChild}
 * (see its {@code category}); every thread that is not a JVM service thread counts as hot-path thread here. Prints
 * "CLA ..." lines: per native window the RESULT and the STACK lines, then the gate's report and "CLA DONE failures=..".
 */
public final class ControlLoopAllocationChild
{
   static final String PREFIX = "CLA ";
   static final long PERIOD_NANOS = 1_000_000L;
   static final int WARMUP = 10_000;
   static final int ITERATIONS = 10_000;
   static final int IMAGE_EVERY_TICKS = 33;
   static final int JOINTS = 30;
   static final int IMAGE_WIDTH = 640;
   static final int IMAGE_HEIGHT = 512;
   static final long SETTLE_TIMEOUT_MILLIS = 30_000;

   private static NativeAllocationCounter counter;
   private static int nativeFailures;
   private static int nativeWindows;

   // Messages (created during setup)
   private static final sensor_msgs.JointState jointState = new sensor_msgs.JointState();
   private static final sensor_msgs.Image image = new sensor_msgs.Image();
   private static final geometry_msgs.PoseStamped estimateIn = new geometry_msgs.PoseStamped();
   private static final sensor_msgs.JointState estimatorIn = new sensor_msgs.JointState();
   private static final geometry_msgs.PoseStamped estimateOut = new geometry_msgs.PoseStamped();
   private static final sensor_msgs.JointState monitorJointIn = new sensor_msgs.JointState();
   private static final sensor_msgs.Image monitorImageIn = new sensor_msgs.Image();
   private static final Statistics statistics = new Statistics();

   // Endpoints
   private static ROS2Publisher<sensor_msgs.JointState> jointPublisher;
   private static AsyncROS2Publisher<sensor_msgs.Image> imagePublisher;
   private static ROS2Publisher<geometry_msgs.PoseStamped> estimatePublisher;
   private static ROS2Subscription<geometry_msgs.PoseStamped> estimatePolling;
   private static ROS2Subscription<sensor_msgs.JointState> estimatorJoints;
   private static ROS2Subscription<sensor_msgs.JointState> monitorJoints;
   private static ROS2Subscription<sensor_msgs.Image> monitorImages;

   // Progress (tick numbers are carried in header.stamp.sec)
   private static long jointPublished;
   private static int lastJointTick = -1;
   private static long imagesPublished;
   private static int lastImageTick = -1;
   private static final AtomicLong estimatorReceived = new AtomicLong();
   private static final AtomicLong monitorJointsReceived = new AtomicLong();
   private static final AtomicLong monitorImagesReceived = new AtomicLong();
   private static final AtomicLong receiveFailures = new AtomicLong();
   private static volatile int monitorLastJointTick = -1;
   private static volatile int monitorLastImageTick = -1;
   private static volatile int estimatorLastTick = -1;
   private static int polledEstimateTick = -1;
   private static long polledEstimates;
   private static long nextTick;
   private static long overruns;
   private static long querySum;

   private static final BooleanSupplier settled = ControlLoopAllocationChild::settled;

   private ControlLoopAllocationChild()
   {
   }

   public static void main(String[] args) throws Exception
   {
      counter = NativeAllocationCounter.open();
      String suffix = Long.toString(ProcessHandle.current().pid());
      // KEEP_LAST deep enough for 100 ms of 1 kHz traffic: a RELIABLE KEEP_LAST writer drops unacknowledged samples when
      // its history is full, and this loop does not wait for delivery between ticks.
      ROS2QoSProfile reliable = new ROS2QoSProfile(ROS2QoSProfile.RELIABLE);
      reliable.depth(100);
      ROS2QoSProfile imageQos = new ROS2QoSProfile(ROS2QoSProfile.RELIABLE);
      imageQos.depth(10);

      ROS2CallbackExecutor executor = new ROS2CallbackExecutor("hp-exec");
      ROS2Context context = ROS2Context.create();
      ROS2Node controller = new ROS2Node("cla_controller", ROS2NodeOptions.defaults().context(context));
      ROS2Node estimator = new ROS2Node("cla_estimator", ROS2NodeOptions.defaults().context(context).callbackExecutor(executor));
      AsyncROS2Node camera = new AsyncROS2Node("cla_camera", ROS2NodeOptions.defaults().context(context), runnable ->
      {
         Thread thread = new Thread(runnable, "hp-async-pub");
         thread.setDaemon(true);
         return thread;
      });
      ROS2Node monitor = new ROS2Node("cla_monitor");

      ROS2Topic<sensor_msgs.JointState> jointTopic = new ROS2Topic<>("/cla_" + suffix + "/joint_states", sensor_msgs.JointState.class);
      ROS2Topic<sensor_msgs.Image> imageTopic = new ROS2Topic<>("/cla_" + suffix + "/camera/image_raw", sensor_msgs.Image.class);
      ROS2Topic<geometry_msgs.PoseStamped> estimateTopic = new ROS2Topic<>("/cla_" + suffix + "/estimate", geometry_msgs.PoseStamped.class);

      fillMessages();
      estimatorJoints = estimator.createSubscription(jointTopic, reader ->
      {
         if (!reader.read(estimatorIn))
         {
            receiveFailures.incrementAndGet();
            return;
         }
         int tick = estimatorIn.getHeader().getStamp().getSec();
         estimateOut.getHeader().getStamp().setSec(tick);
         estimateOut.getHeader().getStamp().setNanosec(estimatorIn.getHeader().getStamp().getNanosec());
         estimateOut.getPose().getPosition().setX(estimatorIn.getPosition().get(0));
         estimateOut.getPose().getPosition().setY(estimatorIn.getVelocity().get(1));
         estimatePublisher.publish(estimateOut);
         estimatorLastTick = tick;
         estimatorReceived.incrementAndGet();
      }, reliable);
      monitorJoints = monitor.createSubscription(jointTopic, reader ->
      {
         if (reader.read(monitorJointIn) && monitorJointIn.getName().size() == JOINTS)
         {
            monitorLastJointTick = monitorJointIn.getHeader().getStamp().getSec();
            monitorJointsReceived.incrementAndGet();
         }
         else
         {
            receiveFailures.incrementAndGet();
         }
      }, reliable);
      monitorImages = monitor.createSubscription(imageTopic, reader ->
      {
         if (reader.read(monitorImageIn) && monitorImageIn.getData().size() == 3 * IMAGE_WIDTH * IMAGE_HEIGHT
             && monitorImageIn.getData().get(0) == (byte) monitorImageIn.getHeader().getStamp().getSec())
         {
            monitorLastImageTick = monitorImageIn.getHeader().getStamp().getSec();
            monitorImagesReceived.incrementAndGet();
         }
         else
         {
            receiveFailures.incrementAndGet();
         }
      }, imageQos);
      estimatePolling = controller.createSubscription(estimateTopic, reliable);
      jointPublisher = controller.createPublisher(jointTopic, reliable);
      estimatePublisher = estimator.createPublisher(estimateTopic, reliable);
      imagePublisher = camera.createPublisher(imageTopic, imageQos);

      jointPublisher.setStatisticsEnabled(true);
      estimatePublisher.setStatisticsEnabled(true);
      imagePublisher.setStatisticsEnabled(true);
      estimatePolling.setStatisticsEnabled(true);
      estimatorJoints.setStatisticsEnabled(true);
      monitorJoints.setStatisticsEnabled(true);
      monitorImages.setStatisticsEnabled(true);

      awaitMatched(jointPublisher, 2);
      awaitMatched(estimatePublisher, 1);
      awaitMatched(imagePublisher, 1);
      System.out.println(PREFIX + "CONFIG intraprocessDelivery=" + System.getenv("FASTDDS_INTRAPROCESS_DELIVERY") + " nativeAbiV2=" + abiV2(jointPublisher)
                         + " contextNodes=" + context.getNodes().size() + " separateNode=" + monitor.getName());

      AllocationGate.Result[] result = new AllocationGate.Result[1];
      Throwable[] error = new Throwable[1];
      Thread control = new Thread(() ->
      {
         try
         {
            result[0] = AllocationGate.named("control loop: 1 kHz JointState, 30 Hz 1 MB Image (async), executor + inline callbacks, polling, statistics")
                                      .warmup(WARMUP)
                                      .iterations(ITERATIONS)
                                      .beforeWindow(() -> nextTick = 0)
                                      .settle(() -> SpinWait.await(settled, SETTLE_TIMEOUT_MILLIS, "control loop delivery"))
                                      .windowListener(new NativeWindows())
                                      .run(ControlLoopAllocationChild::tick);
         }
         catch (Throwable t)
         {
            error[0] = t;
         }
      }, "hp-control");
      control.start();
      control.join();

      int failures = nativeFailures;
      if (error[0] != null)
      {
         System.out.println(PREFIX + "ERROR " + error[0]);
         error[0].printStackTrace(System.out);
         failures++;
      }
      else
      {
         System.out.println(PREFIX + "JAVA mode=" + result[0].mode + " allocationFree=" + result[0].isAllocationFree());
         System.out.println(result[0].report());
         if (!result[0].isAllocationFree())
            failures++;
      }
      String delivery = "jointsPublished=" + jointPublished + " monitorJoints=" + monitorJointsReceived.get() + " estimator=" + estimatorReceived.get()
                        + " polledEstimates=" + polledEstimates + " imagesPublished=" + imagesPublished + " monitorImages=" + monitorImagesReceived.get()
                        + " imagesDropped=" + imagePublisher.getDroppedMessageCount() + " receiveFailures=" + receiveFailures.get() + " overruns=" + overruns
                        + " lastJointTick=" + lastJointTick + " monitorLastJointTick=" + monitorLastJointTick + " estimatorLastTick=" + estimatorLastTick
                        + " polledEstimateTick=" + polledEstimateTick + " lastImageTick=" + lastImageTick + " monitorLastImageTick=" + monitorLastImageTick
                        + " nativeWindows=" + nativeWindows + " querySum=" + querySum;
      System.out.println(PREFIX + "DELIVERY " + delivery);
      boolean delivered = error[0] == null && receiveFailures.get() == 0 && monitorLastJointTick == lastJointTick && estimatorLastTick == lastJointTick
                          && polledEstimateTick == lastJointTick && monitorLastImageTick == lastImageTick && 100 * monitorJointsReceived.get() >= 99 * jointPublished
                          && 100 * estimatorReceived.get() >= 99 * jointPublished && imagesPublished > 0
                          && 10 * monitorImagesReceived.get() >= 9 * imagesPublished;
      if (!delivered)
         failures++;
      System.out.println(PREFIX + "DONE failures=" + failures + " delivered=" + delivered);
      System.out.flush();

      monitor.close();
      camera.close();
      estimator.close();
      controller.close();
      context.close();
      executor.close();
      System.exit(0);
   }

   private static boolean abiV2(ROS2Publisher<?> publisher)
   {
      try
      {
         java.lang.reflect.Method method = ROS2Publisher.class.getDeclaredMethod("usesNativeAbiV2");
         method.setAccessible(true);
         return (Boolean) method.invoke(publisher);
      }
      catch (ReflectiveOperationException e)
      {
         throw new IllegalStateException(e);
      }
   }

   private static void awaitMatched(ROS2Publisher<?> publisher, int subscriptions)
   {
      long deadline = System.nanoTime() + 30_000_000_000L;
      while (publisher.getPublicationMatchedStatus() < subscriptions)
      {
         if (System.nanoTime() - deadline > 0)
            throw new IllegalStateException("not matched: " + publisher.getTopicName());
         LockSupport.parkNanos(10_000_000L);
      }
      // Both sides: the subscriptions of this process know the publishers too
      for (ROS2Subscription<?> subscription : new ROS2Subscription<?>[] {estimatorJoints, monitorJoints, monitorImages, estimatePolling})
      {
         if (subscription != null && !subscription.waitForPublisher(30_000))
            throw new IllegalStateException("not matched: " + subscription.getTopicName());
      }
   }

   private static void fillMessages()
   {
      jointState.getHeader().setFrameId("base_link");
      for (int j = 0; j < JOINTS; ++j)
      {
         jointState.getName().add(String.format("joint_%02d_actuator", j));
         jointState.getPosition().add(j);
         jointState.getVelocity().add(j);
         jointState.getEffort().add(j);
      }
      image.getHeader().setFrameId("camera_color_optical_frame");
      image.setHeight(IMAGE_HEIGHT);
      image.setWidth(IMAGE_WIDTH);
      image.setEncoding("rgb8");
      image.setStep(3 * IMAGE_WIDTH);
      IDLByteSequence data = image.getData();
      data.ensureMinCapacity(3 * IMAGE_WIDTH * IMAGE_HEIGHT);
      for (int i = 0; i < 3 * IMAGE_WIDTH * IMAGE_HEIGHT; ++i)
         data.add((byte) i);
      estimateOut.getHeader().setFrameId("odom");
   }

   /** One 1 ms control tick. Allocation-free. */
   private static void tick(int i)
   {
      if (nextTick == 0)
         nextTick = System.nanoTime();

      jointState.getHeader().getStamp().setSec(i);
      jointState.getHeader().getStamp().setNanosec(i % 1_000_000_000);
      for (int j = 0; j < JOINTS; ++j)
      {
         jointState.getPosition().putAt(j, i + j * 0.01);
         jointState.getVelocity().putAt(j, -i);
         jointState.getEffort().putAt(j, j);
      }
      jointPublisher.publish(jointState);
      jointPublished++;
      lastJointTick = i;

      if (i % IMAGE_EVERY_TICKS == 0)
      {
         image.getHeader().getStamp().setSec(i);
         image.getData().putAt(0, (byte) i);
         imagePublisher.publish(image);
         imagesPublished++;
         lastImageTick = i;
      }

      pollEstimate();
      querySum += queries();

      // Sleep until the next period; after an overrun, start the next tick at once.
      nextTick += PERIOD_NANOS;
      long sleep = nextTick - System.nanoTime();
      if (sleep > 0)
      {
         LockSupport.parkNanos(sleep);
      }
      else
      {
         overruns++;
         nextTick = System.nanoTime();
      }
   }

   private static void pollEstimate()
   {
      if (estimatePolling.readLatest(estimateIn) > 0)
      {
         polledEstimateTick = estimateIn.getHeader().getStamp().getSec();
         polledEstimates++;
      }
   }

   /** Per-tick queries of a control loop. Allocation-free. */
   private static long queries()
   {
      long sum = estimatePolling.getUnreadMessageCount() + jointPublisher.getPublicationMatchedStatus() + imagePublisher.getQueuedMessageCount();
      sum += imagePublisher.getDroppedMessageCount();
      sum += (long) jointPublisher.getMessagePeriod(Statistics.StatisticDataType.AVERAGE);
      sum += (long) jointPublisher.getMessageAge(Statistics.StatisticDataType.MAXIMUM);
      sum += (long) imagePublisher.getMessageSize(Statistics.StatisticDataType.SAMPLE_COUNT);
      sum += (long) monitorJoints.getMessageAge(Statistics.StatisticDataType.STDDEV);
      sum += (long) estimatePolling.getMessagePeriod(Statistics.StatisticDataType.AVERAGE);
      monitorImages.readMessageSizeStatistics(statistics);
      sum += (long) statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT);
      estimatorJoints.readMessagePeriodStatistics(statistics);
      sum += (long) statistics.get(Statistics.StatisticDataType.AVERAGE);
      return sum;
   }

   /**
    * Settle step (inside the windows): the JointState of the last tick has arrived at both subscriptions, its estimate was
    * polled and the last image arrived. (A RELIABLE KEEP_LAST writer replaces samples that a stalled reader has not
    * acknowledged yet, so a sample may be lost; the numbers delivered are checked and reported after the run.)
    */
   private static boolean settled()
   {
      pollEstimate();
      return monitorLastJointTick == lastJointTick && estimatorLastTick == lastJointTick && polledEstimateTick == lastJointTick
             && monitorLastImageTick == lastImageTick;
   }

   /** Native counter armed in the instrumenter and thread-allocated-bytes windows of the gate. */
   private static final class NativeWindows implements AllocationGate.WindowListener
   {
      // Armed in the instrumenter and thread-allocated-bytes windows, and once after the warm-up (unread), so that the
      // first window does not run arm()/disarm() for the first time.
      private static boolean measured(int pass)
      {
         return pass == AllocationGate.PASS_INSTRUMENTER || pass == AllocationGate.PASS_THREAD_BYTES || pass == AllocationGate.PASS_WARMUP;
      }

      @Override
      public void opened(int pass)
      {
         if (measured(pass))
            counter.arm();
      }

      @Override
      public void closed(int pass)
      {
         if (measured(pass))
            counter.disarm();
      }

      @Override
      public void finished(int pass) throws Exception
      {
         if (pass != AllocationGate.PASS_INSTRUMENTER && pass != AllocationGate.PASS_THREAD_BYTES)
            return;
         String name = pass == AllocationGate.PASS_INSTRUMENTER ? "instrumenter-window" : "thread-bytes-window";
         LockSupport.parkNanos(20_000_000L);
         NativeAllocationCounter.Window window = counter.read();
         NativeHeapAllocationChild.Summary summary = NativeHeapAllocationChild.report(PREFIX.trim() + "-" + name, window, ITERATIONS, -1, Map.of(),
                                                                                       ControlLoopAllocationChild::classify);
         long gated = summary.library + summary.javaNative + summary.monitor + summary.jvm + summary.discovery;
         boolean failed = gated != 0 || window.droppedThreads != 0 || window.unslottedCalls != 0 || window.internalCalls != 0;
         System.out.println(PREFIX + "NATIVE " + name + " hotPathCalls=" + summary.library + " javaNativeCalls=" + summary.javaNative + " monitorInflationCalls="
                            + summary.monitor + " hotPathJvmInternalCalls=" + summary.jvm + " discoveryCalls=" + summary.discovery + " jitCalls=" + summary.jit
                            + " gcCalls=" + summary.gc + " handshakeCalls=" + summary.handshake + " backgroundCalls=" + summary.background
                            + " fastddsThreadCalls=" + summary.fastddsThreadCalls + " droppedThreads=" + window.droppedThreads + " unslottedCalls="
                            + window.unslottedCalls + " shimInternalCalls=" + window.internalCalls + " totalCalls=" + window.totalCalls + " failed=" + failed);
         System.out.flush();
         nativeWindows++;
         if (failed)
            nativeFailures++;
      }
   }

   /**
    * Native thread names (as the kernel knows them, at most 15 characters): "fastdds" for the threads Fast-DDS creates,
    * "jvm" for JVM service threads and the gate's observer (background, reported), "hot-java" for everything else: the
    * control thread, the async publish thread, the executor worker, the main thread and any thread nobody expected.
    */
   static String classify(String threadName)
   {
      // STACK lines write spaces in thread names as underscores
      String name = threadName.replace('_', ' ');
      if (name.startsWith("dds.") || name.startsWith("fastdds-callbac"))
         return "fastdds";
      String[] jvm = {"C1 CompilerThre", "C2 CompilerThre", "VM Thread", "VM Periodic Tas", "GC Thread", "G1 ", "Service Thread", "Monitor Deflati", "Signal Dispatch",
                      "Finalizer", "Reference Handl", "Common-Cleaner", "Notification Th", "Sweeper thread", "Attach Listene", "JFR ", "jros2-allocatio"};
      for (String prefix : jvm)
      {
         if (name.startsWith(prefix))
            return "jvm";
      }
      return "hot-java";
   }
}
