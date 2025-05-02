package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;

import java.util.concurrent.atomic.AtomicInteger;

public class AsyncROS2Publisher<T extends ROS2Message<T>> extends ROS2Publisher<T>
{
   private final AsyncROS2Node node;

   private final T[] messagesToPublish;
   private int capacity;
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
      capacity = 16;
      position = 0;
      queueSize = new AtomicInteger(0);
      //noinspection unchecked
      messagesToPublish = (T[]) new ROS2Message[capacity];
      for (int i = 0; i < capacity; ++i)
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
            if (queueSize.getAndIncrement() >= capacity)
            {
               // TODO: Better behavior/message
               queueSize.decrementAndGet();
               throw new RuntimeException("Exceeded queue size :(");
            }

            messagesToPublish[position].set(message);
            node.addTask(() -> publishTask(messagesToPublish[position]));
            position = (position + 1) % capacity;
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
