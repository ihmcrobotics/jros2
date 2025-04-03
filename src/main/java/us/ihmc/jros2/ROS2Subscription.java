package us.ihmc.jros2;

import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.annotation.Const;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.pointers.SubscriptionMatchedStatus;
import us.ihmc.fastddsjava.pointers.fastddsjava;
import us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnDataCallback;
import us.ihmc.fastddsjava.pointers.fastddsjavaInfoMapper.fastddsjava_OnSubscriptionCallback;
import us.ihmc.fastddsjava.pointers.fastddsjava_DataReaderListener;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;

import java.io.Closeable;
import java.nio.ByteBuffer;

import static us.ihmc.fastddsjava.pointers.fastddsjava.fastddsjava_create_datareader;

public class ROS2Subscription<T extends ROS2Message<T>> implements Closeable
{
   protected Pointer fastddsSubscriber;
   private Pointer fastddsDataReader;
   private final ROS2TopicData topicData;
   private final fastddsjava_TopicDataWrapper topicDataWrapper;

   private fastddsjava_OnDataCallback fastddsDataCallback;
   private fastddsjava_OnSubscriptionCallback fastddsSubscriptionCallback;
   private fastddsjava_DataReaderListener fastddsDataReaderListener;
   private ROS2SubscriptionCallback callback;

   private ByteBuffer readBuffer;
   private CDRBuffer cdrBuffer;

   protected ROS2Subscription(Pointer fastddsSubscriber, String subscriberProfileName, ROS2SubscriptionCallback<T> callback, ROS2TopicData topicData)
   {
      this.fastddsSubscriber = fastddsSubscriber;
      this.fastddsDataReader = fastddsjava_create_datareader(fastddsSubscriber, topicData.fastddsTopic, null, subscriberProfileName);
      this.topicData = topicData;
      topicDataWrapper = new fastddsjava_TopicDataWrapper(topicData.topicDataWrapperType.create_data());

      fastddsDataCallback = new fastddsjava_OnDataCallback()
      {
         @Override
         public void call(Pointer dataReader)
         {
            onDataCallback(dataReader);
         }
      };
      fastddsSubscriptionCallback = new fastddsjava_OnSubscriptionCallback()
      {
         @Override
         public void call(Pointer dataReader, SubscriptionMatchedStatus info)
         {
            onSubscriptionCallback(dataReader, info);
         }
      };
      fastddsDataReaderListener = new fastddsjava_DataReaderListener();
      fastddsDataReaderListener.set_on_data_available_callback(fastddsDataCallback);
      fastddsDataReaderListener.set_on_subscription_callback(fastddsSubscriptionCallback);

      readBuffer = ByteBuffer.allocate(1);
      cdrBuffer = new CDRBuffer(readBuffer);
   }

   protected void onDataCallback(Pointer dataReader)
   {
      if (callback != null)
      {

      }
   }

   protected void onSubscriptionCallback(Pointer dataReader, @Const SubscriptionMatchedStatus info)
   {

   }

   @Override
   public void close()
   {
      fastddsjava.fastddsjava_delete_datareader(fastddsSubscriber, fastddsDataReader);

      fastddsDataReaderListener.close();
      fastddsDataCallback.close();
      fastddsSubscriptionCallback.close();
   }
}
