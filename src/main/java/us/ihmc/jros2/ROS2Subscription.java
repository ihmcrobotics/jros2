package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.pointers.SampleInfo;
import us.ihmc.fastddsjava.pointers.SubscriptionMatchedStatus;
import us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnDataCallback;
import us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnSubscriptionCallback;
import us.ihmc.fastddsjava.pointers.fastddsjava_DataReaderListener;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static us.ihmc.fastddsjava.fastddsjavaTools.retcodePrintOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public class ROS2Subscription<T extends ROS2Message<T>>
{
   static
   {
      jros2.load();
   }

   private final Pointer fastddsSubscriber;
   private final Pointer fastddsDataReader;

   private final fastddsjava_DataReaderListener listener;

   private final fastddsjava_OnDataCallback fastddsDataCallback;
   private final fastddsjava_OnSubscriptionCallback fastddsSubscriptionCallback;

   private final TopicData topicData;
   private final fastddsjava_TopicDataWrapper topicDataWrapper;
   private final CDRBuffer cdrBuffer;
   private final SampleInfo sampleInfo;

   private final ROS2SubscriptionCallback<T> callback;
   private final ROS2SubscriptionReader<T> subscriptionReader;

   private final SubscriptionMatchedStatus subscriptionMatchedStatus;

   private final ReadWriteLock closeLock;
   private boolean closed;

   /**
    * Use {@link ROS2Node#createSubscription(ROS2Topic, ROS2SubscriptionCallback, ROS2QoSProfile)}
    */
   protected ROS2Subscription(Pointer fastddsParticipant, String subscriberProfileName, ROS2SubscriptionCallback<T> callback, ROS2Topic<T> topic, TopicData topicData)
   {
      this.callback = callback;
      this.topicData = topicData;

      closeLock = new ReentrantReadWriteLock(true);
      closed = false;

      cdrBuffer = new CDRBuffer();
      sampleInfo = new SampleInfo();
      subscriptionReader = new ROS2SubscriptionReader<>(cdrBuffer, topic);

      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());

      fastddsDataCallback = new fastddsjava_OnDataCallback()
      {
         @Override
         public void call()
         {
            onDataCallback();
         }
      };
      fastddsSubscriptionCallback = new fastddsjava_OnSubscriptionCallback()
      {
         @Override
         public void call()
         {
            onSubscriptionCallback();
         }
      };
      listener = new fastddsjava_DataReaderListener();
      listener.set_on_data_available_callback(fastddsDataCallback);
      listener.set_on_subscription_callback(fastddsSubscriptionCallback);
      subscriptionMatchedStatus = new SubscriptionMatchedStatus();

      fastddsSubscriber = fastddsjava_create_subscriber(fastddsParticipant, subscriberProfileName);
      fastddsDataReader = fastddsjava_create_datareader(fastddsSubscriber, topicData.fastddsTopic, null, subscriberProfileName);
      fastddsjava_datareader_set_listener(fastddsDataReader, listener);
   }

   public int getUnreadCount()
   {
      return fastddsjava_datareader_get_unread_count(fastddsDataReader);
   }

   private void onDataCallback()
   {
      closeLock.readLock().lock();
      try
      {
         if (callback != null && !closed)
         {
            final int ok = RETCODE_OK();

            while (ok == fastddsjava_datareader_take_next_sample(fastddsDataReader, topicDataWrapper, sampleInfo))
            {
               int payloadSizeBytes = (int) topicDataWrapper.data_vector().size();
               cdrBuffer.getBufferUnsafe().rewind();
               cdrBuffer.ensureRemainingCapacity(payloadSizeBytes);
               topicDataWrapper.data_ptr().get(cdrBuffer.getBufferUnsafe().array(), 0, payloadSizeBytes);

               callback.onMessage(subscriptionReader);
            }
         }
      }
      finally
      {
         closeLock.readLock().unlock();
      }
   }

   private void onSubscriptionCallback()
   {
      // TODO:
//      retcodePrintOnError(fastddsjava_datareader_get_subscription_matched_status(fastddsDataReader, subscriptionMatchedStatus));
   }

   protected void close(Pointer fastddsParticipant)
   {
      closeLock.writeLock().lock();
      boolean wasClosed = closed;
      closed = true;
      closeLock.writeLock().unlock();

      if (!wasClosed)
      {
         retcodePrintOnError(fastddsjava_delete_datareader(fastddsSubscriber, fastddsDataReader));

         listener.close();
         fastddsDataCallback.close();
         fastddsSubscriptionCallback.close();

         topicData.topicDataWrapperType.delete_data(topicDataWrapper);
         sampleInfo.close();
         topicDataWrapper.close();

         retcodePrintOnError(fastddsjava_delete_subscriber(fastddsParticipant, fastddsSubscriber));
      }
   }
}
