package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import us.ihmc.fastddsjava.fastddsjavaException;
import us.ihmc.fastddsjava.fastddsjavaTools;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.SubscriptionMatchedStatus;
import us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnDataCallback;
import us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnSubscriptionCallback;
import us.ihmc.fastddsjava.pointers.fastddsjava_DataReaderListener;
import us.ihmc.log.LogTools;

import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public class ROS2Subscription<T extends ROS2Message<T>>
{
   static
   {
      fastddsjavaNativeLibrary.load();
   }

   private final Pointer fastddsSubscriber;
   private final Pointer fastddsDataReader;

   private final fastddsjava_OnDataCallback fastddsDataCallback;
   private final fastddsjava_OnSubscriptionCallback fastddsSubscriptionCallback;
   private final fastddsjava_DataReaderListener fastddsDataReaderListener;
   private final SubscriptionMatchedStatus subscriptionMatchedStatus;
   private final ROS2SubscriptionReader<T> subscriptionReader;
   private final ROS2SubscriptionCallback<T> callback;

   protected ROS2Subscription(Pointer fastddsParticipant, String subscriberProfileName, ROS2SubscriptionCallback<T> callback, ROS2TopicData topicData)
   {
      this.callback = callback;

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
      fastddsDataReaderListener = new fastddsjava_DataReaderListener();
      fastddsDataReaderListener.set_on_data_available_callback(fastddsDataCallback);
      fastddsDataReaderListener.set_on_subscription_callback(fastddsSubscriptionCallback);
      subscriptionMatchedStatus = new SubscriptionMatchedStatus();

      this.fastddsSubscriber = fastddsjava_create_subscriber(fastddsParticipant, subscriberProfileName);
      this.fastddsDataReader = fastddsjava_create_datareader(fastddsSubscriber, topicData.fastddsTopic, fastddsDataReaderListener, subscriberProfileName);

      subscriptionReader = new ROS2SubscriptionReader<>(fastddsDataReader, topicData);
   }

   private void onDataCallback()
   {
      if (callback != null)
      {
         callback.onMessage(subscriptionReader);
      }
   }

   private void onSubscriptionCallback()
   {
      int ret = fastddsjava_datareader_get_subscription_matched_status(fastddsDataReader, subscriptionMatchedStatus);
      try
      {
         fastddsjavaTools.retcodeThrowOnError(ret);
      }
      catch (fastddsjavaException e)
      {
         LogTools.error(e);
      }
   }

   protected void close(Pointer fastddsParticipant)
   {
      int ret = fastddsjava_delete_datareader(fastddsSubscriber, fastddsDataReader);
      try
      {
         fastddsjavaTools.retcodeThrowOnError(ret);
      }
      catch (fastddsjavaException e)
      {
         LogTools.error(e);
      }

      fastddsDataReaderListener.close();
      fastddsDataCallback.close();
      fastddsSubscriptionCallback.close();
      subscriptionReader.close();

      ret = fastddsjava_delete_subscriber(fastddsParticipant, fastddsSubscriber);
      try
      {
         fastddsjavaTools.retcodeThrowOnError(ret);
      }
      catch (fastddsjavaException e)
      {
         LogTools.error(e);
      }
   }
}
