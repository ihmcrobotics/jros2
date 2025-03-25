package us.ihmc.fastddsjava;

import jakarta.xml.bind.JAXBElement;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerScope;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
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

   @RepeatedTest(5000)
   public void publishAndSubscribeTest() throws InterruptedException
   {
      int retCode;
      final byte[] sampleData = generateRandomBytes(100000);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001);
      Pointer typeSupport = fastddsjava_create_type_support(topicDataWrapperType);

      Pointer participant = fastddsjava_create_participant("example_participant");

      retCode = fastddsjava_register_type(participant, typeSupport);
      checkReturnCode(retCode);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "example_topic", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_publisher");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "example_subscriber");
      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();

      final AtomicBoolean received = new AtomicBoolean(false);
      final AtomicBoolean dataCorrect = new AtomicBoolean(false);

      // Add callback to listener
      Pointer dataReceive = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapperReceive = new fastddsjava_TopicDataWrapper(dataReceive);
      SampleInfo sampleInfo = new SampleInfo();
      fastddsjava_OnDataCallback onDataCallback = new fastddsjava_OnDataCallback()
      {
         @Override
         public void call(Pointer dataReader)
         {
            synchronized (received)
            {
               fastddsjava_datareader_read_next_sample(dataReader, topicDataWrapperReceive, sampleInfo);
               dataCorrect.set(Arrays.equals(sampleData, topicDataWrapperReceive.data_vector().get()));

               received.set(true);
               received.notify();
            }
         }
      };
      listener.set_on_data_available_callback(onDataCallback);

      // Add subscription callback
      final AtomicInteger numberOfMatches = new AtomicInteger();
      fastddsjava_OnSubscriptionCallback onSubscriptionCallback = new fastddsjava_OnSubscriptionCallback() {
         @Override
         public void call(Pointer dataReader, SubscriptionMatchedStatus info)
         {
            // Record number of matches
            numberOfMatches.set(info.total_count());
         }
      };
      listener.set_on_subscription_callback(onSubscriptionCallback);

      Pointer data = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(data);

      // pack wrapper with data
      topicDataWrapper.data_vector().put(sampleData);

      // Create reader with listener
      Pointer dataReader = fastddsjava_create_datareader(subscriber, topic, listener, "example_subscriber");

      // Send the data
      retCode = fastddsjava_datawriter_write(dataWriter, topicDataWrapper);
      checkReturnCode(retCode);

      // Wait to receive data
      synchronized (received)
      {
         if (!received.get())
         {
            received.wait();
         }
      }

      // assert there's only 1 match
      assertEquals(1, numberOfMatches.get());

      // assert the data was received correctly
      assertTrue(dataCorrect.get());

      // delete/release all references
      assertTrue(sampleInfo.releaseReference());
      topicDataWrapperType.delete_data(dataReceive);
      topicDataWrapperType.delete_data(data);
      checkReturnCode(fastddsjava_delete_datareader(subscriber, dataReader));
      assertTrue(onSubscriptionCallback.releaseReference());
      assertTrue(onDataCallback.releaseReference());
      assertTrue(listener.releaseReference());
      checkReturnCode(fastddsjava_delete_subscriber(participant, subscriber));
      checkReturnCode(fastddsjava_delete_datawriter(publisher, dataWriter));
      checkReturnCode(fastddsjava_delete_publisher(participant, publisher));
      checkReturnCode(fastddsjava_delete_topic(participant, topic));
      checkReturnCode(fastddsjava_unregister_type(participant, topicDataWrapperType.get_name()));
      checkReturnCode(fastddsjava_delete_participant(participant));
//      assertTrue(typeSupport.releaseReference()); // TODO: Look into whether we need to explicitly deallocate this
      assertTrue(topicDataWrapperType.releaseReference());
   }

   @Test
   public void growingDataTest() throws InterruptedException
   {
      final int initialSize = 1;
      final int finalSize = 1000;
      final AtomicInteger currentSize = new AtomicInteger(initialSize);

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("test_type", (short) 0x0001);
      Pointer typeSupport = fastddsjava_create_type_support(topicDataWrapperType);

      Pointer participant = fastddsjava_create_participant("example_participant");

      fastddsjava_register_type(participant, typeSupport);
      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "example_topic", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_publisher");

      // Subscriber
      Pointer subscriber = fastddsjava_create_subscriber(participant, "example_subscriber");
      fastddsjava_DataReaderListener listener = new fastddsjava_DataReaderListener();
      final AtomicBoolean finished = new AtomicBoolean(false);
      listener.set_on_data_available_callback(new fastddsjava_OnDataCallback()
      {
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

   private void checkReturnCode(int error)
   {
      if (error != RETCODE_OK())
         throw new RuntimeException("ERROR " + error);
   }
}
