package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.log.LogTools;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ROS2AsyncPublisher<T extends ROS2Message<T>> extends ROS2Publisher<T>
{
   private final Thread publishThread;
   private final BlockingQueue<T> publishQueue;

   protected ROS2AsyncPublisher(Pointer fastddsParticipant, String publisherProfileName, TopicData topicData)
   {
      super(fastddsParticipant, publisherProfileName, topicData);

      // TODO: Daemon, name, etc
      publishThread = new Thread(this::publishLoop);

      // TODO: capacity param in constructor?
      publishQueue = new ArrayBlockingQueue<>(16);

      publishThread.start();
   }

   @Override
   public void publish(T message)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            // TODO: offer and throw custom exception?
            publishQueue.add(message);
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }
   }

   private void publishLoop()
   {
      while (true)
      {
         try
         {
            super.publish(publishQueue.take());
         }
         catch (InterruptedException interrupted)
         {
            // TODO: publish remaining messages in queue?
            break;
         }
      }
   }

   @Override
   protected void close(Pointer fastddsParticipant)
   {
      publishThread.interrupt();
      try
      {
         publishThread.join();
      }
      catch (InterruptedException interruptedException)
      {
         LogTools.error("Failed to stop publish thread");
      }

      super.close(fastddsParticipant);
   }
}
