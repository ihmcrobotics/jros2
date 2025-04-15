package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public class ROS2Publisher<T extends ROS2Message<T>>
{
   static
   {
      jros2.load();
   }

   private final Pointer fastddsPublisher;
   private final Pointer fastddsDataWriter;
   private final TopicData topicData;
   private final fastddsjava_TopicDataWrapper topicDataWrapper;
   private final CDRBuffer cdrBuffer;

   private final ReadWriteLock closeLock;
   private boolean closed;

   /**
    * Use {@link ROS2Node#createPublisher(ROS2Topic, ROS2QoSProfile)}
    */
   protected ROS2Publisher(Pointer fastddsParticipant, String publisherProfileName, TopicData topicData)
   {
      this.topicData = topicData;

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());
      fastddsPublisher = fastddsjava_create_publisher(fastddsParticipant, publisherProfileName);
      fastddsDataWriter = fastddsjava_create_datawriter(fastddsPublisher, topicData.fastddsTopic, publisherProfileName);
      cdrBuffer = new CDRBuffer();
   }

   public void publish(T message)
   {
      closeLock.readLock().lock();
      try
      {
         if (!closed)
         {
            synchronized (cdrBuffer)
            {
               int payloadSizeBytes = CDRBuffer.PAYLOAD_HEADER.length + message.calculateSizeBytes();
               cdrBuffer.ensureRemainingCapacity(payloadSizeBytes);

               // TODO: check if we can shrink the writeBuffer to save memory

               cdrBuffer.writePayloadHeader();
               message.serialize(cdrBuffer);

               topicDataWrapper.data_vector().resize(payloadSizeBytes);
               topicDataWrapper.data_ptr().put(cdrBuffer.getBufferUnsafe().array(), 0, payloadSizeBytes);
            }

            retcodePrintOnError(fastddsjava_datawriter_write(fastddsDataWriter, topicDataWrapper));
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }
   }

   protected void close(Pointer fastddsParticipant)
   {
      closeLock.writeLock().lock();
      boolean wasClosed = closed;
      closed = true;
      closeLock.writeLock().unlock();

      if (!wasClosed)
      {
         topicData.topicDataWrapperType.delete_data(topicDataWrapper);

         retcodePrintOnError(fastddsjava_delete_datawriter(fastddsPublisher, fastddsDataWriter));
         retcodePrintOnError(fastddsjava_delete_publisher(fastddsParticipant, fastddsPublisher));
      }
   }
}
