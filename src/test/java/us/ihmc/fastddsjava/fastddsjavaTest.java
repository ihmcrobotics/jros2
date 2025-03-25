package us.ihmc.fastddsjava;

import jakarta.xml.bind.JAXBElement;
import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.SampleInfo;
import us.ihmc.fastddsjava.pointers.SubscriptionMatchedStatus;
import us.ihmc.fastddsjava.pointers.fastddsjava_DataReaderListener;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;
import us.ihmc.fastddsjava.profiles.ProfilesXML;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps;
import us.ihmc.fastddsjava.profiles.gen.ParticipantProfileType.Rtps.UserTransports;
import us.ihmc.fastddsjava.profiles.gen.PublisherProfileType;
import us.ihmc.fastddsjava.profiles.gen.SubscriberProfileType;
import us.ihmc.fastddsjava.profiles.gen.TopicProfileType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorListType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType;
import us.ihmc.fastddsjava.profiles.gen.TransportDescriptorType.InterfaceWhiteList;

import javax.xml.namespace.QName;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public class fastddsjavaTest
{
   static
   {
      fastddsjavaNativeLibrary.load();

      ProfilesXML profilesXML = new ProfilesXML();

      // TODO: Experiment more with this
      profilesXML.getLibrarySettingsType().setIntraprocessDelivery("OFF");

      // Add transport
      TransportDescriptorListType transportDescriptorListType = new TransportDescriptorListType();
      TransportDescriptorType udp4Transport = new TransportDescriptorType();
      udp4Transport.setTransportId(UUID.randomUUID().toString());
      udp4Transport.setType("UDPv4");
      InterfaceWhiteList interfaceWhiteList = new InterfaceWhiteList();
      JAXBElement<String> addressElement = new JAXBElement<>(new QName(ProfilesXML.FAST_DDS_NAMESPACE_URI, "address"),
                                                             String.class,
                                                             "127.0.0.1");
      interfaceWhiteList.getAddressOrInterface().add(addressElement);
      udp4Transport.setInterfaceWhiteList(interfaceWhiteList);

      transportDescriptorListType.getTransportDescriptor().add(udp4Transport);
      profilesXML.addTransportDescriptorsProfile(transportDescriptorListType);

      // Add participant profile
      ParticipantProfileType participantProfileType = new ParticipantProfileType();

      Rtps rtps = new Rtps();
      rtps.setUseBuiltinTransports(true);
      ParticipantProfileType.Rtps.UserTransports userTransports = new UserTransports();
      userTransports.getTransportId().add(udp4Transport.getTransportId());
      rtps.setUserTransports(userTransports);
      participantProfileType.setRtps(rtps);

      participantProfileType.setProfileName("example_participant");
      participantProfileType.setDomainId(145);
      profilesXML.addParticipantProfile(participantProfileType);

      // Add topic profile
      TopicProfileType topicProfileType = new TopicProfileType();
      topicProfileType.setProfileName("example_topic");
      profilesXML.addTopicProfile(topicProfileType);

      // Add publisher profile / AKA data writer profile
      PublisherProfileType publisherProfileType = new PublisherProfileType();
      publisherProfileType.setProfileName("example_publisher");
      profilesXML.addPublisherProfile(publisherProfileType);

      // Add subscriber profile / AKA data reader profile
      SubscriberProfileType subscriberProfileType = new SubscriberProfileType();
      subscriberProfileType.setProfileName("example_subscriber");
      profilesXML.addSubscriberProfile(subscriberProfileType);

      String xmlContent = profilesXML.marshall();

      System.out.println(xmlContent);

      fastddsjava_load_xml_profiles_string(xmlContent);
   }

   private static byte[] generateRandomBytes(int length)
   {
      byte[] randomBytes = new byte[length];
      new Random(621654).nextBytes(randomBytes);
      return randomBytes;
   }

   @Test
   public void createAndDeleteTopicDataWrapperTest()
   {
      int megabytes = 1;
      int dataLength = 1000000 * megabytes;

      byte[] sampleData = generateRandomBytes(dataLength);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001);
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());

      topicDataWrapper.data_vector().put(sampleData);

      topicDataWrapperType.delete_data(topicDataWrapper);
   }

   @RepeatedTest(1000)
   public void publishAndSubscribeTest() throws InterruptedException
   {
//      int megabytes = 1;
//      int dataLength = 1000000 * megabytes;
      final byte[] sampleData = generateRandomBytes(100000);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001);
      topicDataWrapperType.deallocate(false); // TODO: FIX


      Pointer participant = fastddsjava_create_participant("example_participant");
      fastddsjava_register_type(participant, topicDataWrapperType);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "example_topic", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_publisher");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "example_subscriber");
      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();
      final AtomicBoolean received = new AtomicBoolean(false);
      final AtomicBoolean dataCorrect = new AtomicBoolean(false);
      listener.set_on_data_available_callback(new fastddsjava_OnDataCallback() {
         @Override
         public void call(Pointer dataReader)
         {
            fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());
            SampleInfo sampleInfo = new SampleInfo();
            fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapper, sampleInfo);

            dataCorrect.set(Arrays.equals(sampleData, topicDataWrapper.data_vector().get()));

            synchronized (received)
            {
               received.set(true);
               received.notify();
            }
         }
      });
      listener.set_on_subscription_callback(new fastddsjava_OnSubscriptionCallback() {
         @Override
         public void call(Pointer dataReader, SubscriptionMatchedStatus info)
         {
            // Assert there is 1 match
            Assertions.assertEquals(1, info.total_count());
         }
      });
      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, listener, "example_subscriber");

      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());
      topicDataWrapper.data_vector().put(sampleData);
      fastddsjava_datawriter_write(dataWriter, topicDataWrapper);

      if (!received.get())
      {
         synchronized (received)
         {
            received.wait();
         }
      }

      // Assert that the data received by the data reader is the same as was written by the data writer
      Assertions.assertTrue(dataCorrect.get());

      // Assert that internally the data reader does not have any more history to be read
      Assertions.assertEquals(RETCODE_NO_DATA(), fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapper, new SampleInfo()));

      topicDataWrapperType.delete_data(topicDataWrapper);

      fastddsjava_delete_datawriter(publisher, dataWriter);
      fastddsjava_delete_datareader(subscriber, dataReader);
      fastddsjava_delete_publisher(participant, publisher);
      fastddsjava_delete_subscriber(participant, subscriber);
      fastddsjava_delete_topic(participant, topic);
      fastddsjava_unregister_type(participant, topicDataWrapperType.get_name());
      fastddsjava_delete_participant(participant);
   }

   @Test
   public void growingDataTest() throws InterruptedException
   {
      final int initialSize = 1;
      final int finalSize = 1000;
      final AtomicInteger currentSize = new AtomicInteger(initialSize);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001);
      topicDataWrapperType.deallocate(false); // TODO: FIX

      Pointer participant = fastddsjava_create_participant("example_participant");
      fastddsjava_register_type(participant, topicDataWrapperType);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "example_topic", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_publisher");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "example_subscriber");
      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();
      final AtomicBoolean finished = new AtomicBoolean(false);
      listener.set_on_data_available_callback(new fastddsjava_OnDataCallback() {
         @Override
         public void call(Pointer dataReader)
         {
            fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());
            SampleInfo sampleInfo = new SampleInfo();
            fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapper, sampleInfo);

            // Wait to notify until the size is finalSize
            if (topicDataWrapper.data_vector().size() > finalSize)
            {
               synchronized (finished)
               {
                  finished.set(true);
                  finished.notify();
               }
            }

            topicDataWrapperType.delete_data(topicDataWrapper);
         }
      });

      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, listener, "example_subscriber");

      while (!finished.get())
      {
         if (currentSize.get() >= finalSize)
         {
            synchronized (finished)
            {
               finished.wait();
            }
         }
         else
         {
            // Grow the currentSize
            currentSize.set(currentSize.get() * 2);

            fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(topicDataWrapperType.create_data());
            topicDataWrapper.data_vector().put(generateRandomBytes(currentSize.get()));

            fastddsjava_datawriter_write(dataWriter, topicDataWrapper);

            topicDataWrapperType.delete_data(topicDataWrapper);
         }
      }

      fastddsjava_delete_datawriter(publisher, dataWriter);
      fastddsjava_delete_datareader(subscriber, dataReader);
      fastddsjava_delete_publisher(participant, publisher);
      fastddsjava_delete_subscriber(participant, subscriber);
      fastddsjava_delete_topic(participant, topic);
      fastddsjava_unregister_type(participant, topicDataWrapperType.get_name());
      fastddsjava_delete_participant(participant);
   }
}
