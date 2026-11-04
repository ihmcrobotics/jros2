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
package us.ihmc.jros2.allocation.epsilon;

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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Child JVM of {@link EpsilonGcAllocationTest}: the control-loop process of ControlLoopAllocationChild (one
 * {@link ROS2Context} with a 1 kHz sensor_msgs/JointState controller, an estimator on a {@link ROS2CallbackExecutor} and an
 * {@link AsyncROS2Node} camera publishing a 983 kB sensor_msgs/Image at 30 Hz, plus a monitor node with its own participant
 * and inline callbacks; readLatest polling and statistics queries every tick), run with the Epsilon GC, which never frees
 * memory. A sampler thread reads the used heap every 100 ms without allocating.
 * <p>
 * Arguments: warm-up seconds, measured seconds. Prints {@code EPSILON SETUP}, {@code EPSILON RESULT} (used heap after
 * warm-up, at the end, the largest growth seen, delivery counters) and {@code EPSILON DONE}; an OutOfMemoryError ends the
 * process with a non-zero exit code.
 */
public final class EpsilonControlLoopChild
{
   static final String PREFIX = "EPSILON ";
   private static final int JOINTS = 30;
   private static final int IMAGE_WIDTH = 640;
   private static final int IMAGE_HEIGHT = 512;
   private static final int IMAGE_EVERY_TICKS = 33;

   private static final sensor_msgs.JointState jointState = new sensor_msgs.JointState();
   private static final sensor_msgs.Image image = new sensor_msgs.Image();
   private static final geometry_msgs.PoseStamped estimateIn = new geometry_msgs.PoseStamped();
   private static final sensor_msgs.JointState estimatorIn = new sensor_msgs.JointState();
   private static final geometry_msgs.PoseStamped estimateOut = new geometry_msgs.PoseStamped();
   private static final sensor_msgs.JointState monitorJointIn = new sensor_msgs.JointState();
   private static final sensor_msgs.Image monitorImageIn = new sensor_msgs.Image();
   private static final Statistics statistics = new Statistics();

   private static ROS2Publisher<sensor_msgs.JointState> jointPublisher;
   private static AsyncROS2Publisher<sensor_msgs.Image> imagePublisher;
   private static ROS2Publisher<geometry_msgs.PoseStamped> estimatePublisher;
   private static ROS2Subscription<geometry_msgs.PoseStamped> estimatePolling;
   private static ROS2Subscription<sensor_msgs.JointState> estimatorJoints;
   private static ROS2Subscription<sensor_msgs.JointState> monitorJoints;
   private static ROS2Subscription<sensor_msgs.Image> monitorImages;

   private static final AtomicLong estimatorReceived = new AtomicLong();
   private static final AtomicLong monitorJointsReceived = new AtomicLong();
   private static final AtomicLong monitorImagesReceived = new AtomicLong();
   private static final AtomicLong receiveFailures = new AtomicLong();
   private static long jointsPublished;
   private static long imagesPublished;
   private static long polledEstimates;
   private static long querySum;
   private static volatile boolean measuring;
   private static volatile boolean stop;
   private static volatile long baseline;
   private static volatile long maxGrowth;

   private EpsilonControlLoopChild()
   {
   }

   static long usedHeap()
   {
      Runtime runtime = Runtime.getRuntime();
      return runtime.totalMemory() - runtime.freeMemory();
   }

   public static void main(String[] args) throws Exception
   {
      double warmupSeconds = Double.parseDouble(args[0]);
      double measuredSeconds = Double.parseDouble(args[1]);
      String suffix = Long.toString(ProcessHandle.current().pid());
      ROS2QoSProfile reliable = new ROS2QoSProfile(ROS2QoSProfile.RELIABLE);
      reliable.depth(100);
      ROS2QoSProfile imageQos = new ROS2QoSProfile(ROS2QoSProfile.RELIABLE);
      imageQos.depth(10);

      ROS2CallbackExecutor executor = new ROS2CallbackExecutor("hp-exec");
      ROS2Context context = ROS2Context.create();
      ROS2Node controller = new ROS2Node("eps_controller", ROS2NodeOptions.defaults().context(context));
      ROS2Node estimator = new ROS2Node("eps_estimator", ROS2NodeOptions.defaults().context(context).callbackExecutor(executor));
      AsyncROS2Node camera = new AsyncROS2Node("eps_camera", ROS2NodeOptions.defaults().context(context), runnable ->
      {
         Thread thread = new Thread(runnable, "hp-async-pub");
         thread.setDaemon(true);
         return thread;
      });
      ROS2Node monitor = new ROS2Node("eps_monitor");

      ROS2Topic<sensor_msgs.JointState> jointTopic = new ROS2Topic<>("/eps_" + suffix + "/joint_states", sensor_msgs.JointState.class);
      ROS2Topic<sensor_msgs.Image> imageTopic = new ROS2Topic<>("/eps_" + suffix + "/camera/image_raw", sensor_msgs.Image.class);
      ROS2Topic<geometry_msgs.PoseStamped> estimateTopic = new ROS2Topic<>("/eps_" + suffix + "/estimate", geometry_msgs.PoseStamped.class);

      fillMessages();
      estimatorJoints = estimator.createSubscription(jointTopic, reader ->
      {
         if (!reader.read(estimatorIn))
         {
            receiveFailures.incrementAndGet();
            return;
         }
         estimateOut.getHeader().getStamp().setSec(estimatorIn.getHeader().getStamp().getSec());
         estimateOut.getPose().getPosition().setX(estimatorIn.getPosition().get(0));
         estimatePublisher.publish(estimateOut);
         estimatorReceived.incrementAndGet();
      }, reliable);
      monitorJoints = monitor.createSubscription(jointTopic, reader ->
      {
         if (reader.read(monitorJointIn) && monitorJointIn.getName().size() == JOINTS)
            monitorJointsReceived.incrementAndGet();
         else
            receiveFailures.incrementAndGet();
      }, reliable);
      monitorImages = monitor.createSubscription(imageTopic, reader ->
      {
         if (reader.read(monitorImageIn) && monitorImageIn.getData().size() == 3 * IMAGE_WIDTH * IMAGE_HEIGHT)
            monitorImagesReceived.incrementAndGet();
         else
            receiveFailures.incrementAndGet();
      }, imageQos);
      estimatePolling = controller.createSubscription(estimateTopic, reliable);
      jointPublisher = controller.createPublisher(jointTopic, reliable);
      estimatePublisher = estimator.createPublisher(estimateTopic, reliable);
      imagePublisher = camera.createPublisher(imageTopic, imageQos);
      jointPublisher.setStatisticsEnabled(true);
      imagePublisher.setStatisticsEnabled(true);
      estimatePolling.setStatisticsEnabled(true);
      estimatorJoints.setStatisticsEnabled(true);
      monitorJoints.setStatisticsEnabled(true);
      monitorImages.setStatisticsEnabled(true);
      if (!jointPublisher.waitForSubscription(30_000) || !imagePublisher.waitForSubscription(30_000) || !estimatePublisher.waitForSubscription(30_000)
          || !estimatePolling.waitForPublisher(30_000) || !monitorImages.waitForPublisher(30_000))
         throw new IllegalStateException("endpoints did not match");
      System.out.println(PREFIX + "SETUP usedHeap=" + usedHeap() + " maxHeap=" + Runtime.getRuntime().maxMemory() + " intraprocessDelivery="
                         + System.getenv("FASTDDS_INTRAPROCESS_DELIVERY"));

      Thread sampler = new Thread(() ->
      {
         while (!stop)
         {
            if (measuring)
               maxGrowth = Math.max(maxGrowth, usedHeap() - baseline);
            LockSupport.parkNanos(100_000_000L);
         }
      }, "eps-sampler");
      sampler.setDaemon(true);
      sampler.start();

      long warmupTicks = (long) (warmupSeconds * 1000);
      long totalTicks = warmupTicks + (long) (measuredSeconds * 1000);
      Throwable[] error = new Throwable[1];
      long[] afterWarmup = new long[1];
      Thread control = new Thread(() ->
      {
         try
         {
            long next = System.nanoTime();
            for (long i = 0; i < totalTicks; ++i)
            {
               if (i == warmupTicks)
               {
                  afterWarmup[0] = usedHeap();
                  baseline = afterWarmup[0];
                  measuring = true;
               }
               tick((int) i);
               next += 1_000_000L;
               long sleep = next - System.nanoTime();
               if (sleep > 0)
                  LockSupport.parkNanos(sleep);
               else
                  next = System.nanoTime();
            }
         }
         catch (Throwable t)
         {
            error[0] = t;
         }
      }, "hp-control");
      control.start();
      control.join();
      measuring = false;
      long end = usedHeap();
      stop = true;
      if (error[0] != null)
      {
         System.out.println(PREFIX + "ERROR " + error[0]);
         error[0].printStackTrace(System.out);
         System.exit(2);
      }
      Thread.sleep(500); // the last messages
      System.out.println(PREFIX + "RESULT usedAfterWarmup=" + afterWarmup[0] + " usedAtEnd=" + end + " growth=" + (end - afterWarmup[0]) + " maxGrowth="
                         + maxGrowth + " jointsPublished=" + jointsPublished + " monitorJoints=" + monitorJointsReceived.get() + " estimator="
                         + estimatorReceived.get() + " polledEstimates=" + polledEstimates + " imagesPublished=" + imagesPublished + " monitorImages="
                         + monitorImagesReceived.get() + " receiveFailures=" + receiveFailures.get() + " querySum=" + querySum);
      System.out.println(PREFIX + "DONE");
      System.out.flush();
      monitor.close();
      camera.close();
      estimator.close();
      controller.close();
      context.close();
      executor.close();
      System.exit(0);
   }

   private static void fillMessages()
   {
      jointState.getHeader().setFrameId("base_link");
      for (int j = 0; j < JOINTS; ++j)
      {
         jointState.getName().add("joint_" + j);
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
      jointState.getHeader().getStamp().setSec(i);
      for (int j = 0; j < JOINTS; ++j)
      {
         jointState.getPosition().putAt(j, i + j * 0.01);
         jointState.getVelocity().putAt(j, -i);
      }
      jointPublisher.publish(jointState);
      jointsPublished++;
      if (i % IMAGE_EVERY_TICKS == 0)
      {
         image.getHeader().getStamp().setSec(i);
         imagePublisher.publish(image);
         imagesPublished++;
      }
      if (estimatePolling.readLatest(estimateIn) > 0)
         polledEstimates++;
      long sum = estimatePolling.getUnreadMessageCount() + jointPublisher.getPublicationMatchedStatus() + imagePublisher.getQueuedMessageCount();
      sum += (long) jointPublisher.getMessagePeriod(Statistics.StatisticDataType.AVERAGE);
      sum += (long) monitorJoints.getMessageAge(Statistics.StatisticDataType.STDDEV);
      monitorImages.readMessageSizeStatistics(statistics);
      sum += (long) statistics.get(Statistics.StatisticDataType.SAMPLE_COUNT);
      estimatorJoints.readMessagePeriodStatistics(statistics);
      sum += (long) statistics.get(Statistics.StatisticDataType.AVERAGE);
      querySum += sum;
   }
}
