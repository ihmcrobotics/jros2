package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;

import java.util.concurrent.atomic.AtomicInteger;

public class AsyncROS2Publisher<T extends ROS2Message<T>> extends ROS2Publisher<T>
{
   private static final int CAPACITY = 32;

   private final AsyncROS2Node node;

   private final T[] messagesToPublish;
   private int position;
   private final AtomicInteger queueSize;

   /**
    * Use {@link ROS2Node#createPublisher(ROS2Topic, ROS2QoSProfile)}
    */
   protected AsyncROS2Publisher(AsyncROS2Node node, Pointer fastddsParticipant, String publisherProfileName, ROS2Topic<T> topic, TopicData topicData)
   {
      super(fastddsParticipant, publisherProfileName, topicData);

      this.node = node;

      // TODO: how should we set the capacity?
      position = 0;
      queueSize = new AtomicInteger(0);
      //noinspection unchecked
      messagesToPublish = (T[]) new ROS2Message[CAPACITY];
      for (int i = 0; i < CAPACITY; ++i)
      {
         messagesToPublish[i] = ROS2Message.createInstance(topic.topicType());
      }
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

            T messageToPublish = messagesToPublish[position];
            messageToPublish.set(message);

            if (node.addTask(() -> publishTask(messageToPublish)))
            {
               position = (position + 1) % CAPACITY;
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

   private void publishTask(T messageToPublish)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            super.publish(messageToPublish);
         }
      }
      finally
      {
         queueSize.decrementAndGet();
         closeLock.readLock().unlock();
      }
   }
}
