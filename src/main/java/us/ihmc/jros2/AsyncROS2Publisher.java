package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;

import java.util.concurrent.atomic.AtomicInteger;

public class AsyncROS2Publisher<T extends ROS2Message<T>> extends ROS2Publisher<T>
{
   private static final int CAPACITY = 32;

   private final AsyncROS2Node node;

   private final T[] messagesToPublish;
   private int insertPosition;
   private int publishPosition;
   private final AtomicInteger queueSize;

   private final Runnable publishTask;

   /**
    * Use {@link ROS2Node#createPublisher(ROS2Topic, ROS2QoSProfile)}
    */
   protected AsyncROS2Publisher(AsyncROS2Node node, Pointer fastddsParticipant, String publisherProfileName, ROS2Topic<T> topic, TopicData topicData)
   {
      super(fastddsParticipant, publisherProfileName, topicData);

      this.node = node;

      // TODO: how should we set the capacity?
      insertPosition = 0;
      publishPosition = 0;
      queueSize = new AtomicInteger(0);
      //noinspection unchecked
      messagesToPublish = (T[]) new ROS2Message[CAPACITY];
      for (int i = 0; i < CAPACITY; ++i)
      {
         messagesToPublish[i] = ROS2Message.createInstance(topic.topicType());
      }

      publishTask = this::publishTask;
   }

   @Override
   public void publish(T message)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            if (queueSize.getAndIncrement() >= CAPACITY)
            {
               // TODO: Better behavior/message
               queueSize.decrementAndGet();
               throw new RuntimeException("Exceeded queue size :(");
            }

            T messageToPublish = messagesToPublish[insertPosition];
            messageToPublish.set(message);

            if (node.addTask(publishTask))
            {
               insertPosition = (insertPosition + 1) % CAPACITY;
            }
            else
            {
               queueSize.decrementAndGet();
               throw new RuntimeException("AsyncROS2Node did not accept the task");
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }
   }

   private void publishTask()
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            super.publish(messagesToPublish[publishPosition]);
            publishPosition = (publishPosition + 1) % CAPACITY;
         }
      }
      finally
      {
         queueSize.decrementAndGet();
         closeLock.readLock().unlock();
      }
   }
}
