package us.ihmc.fastddsjava;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;
import us.ihmc.jros2.testmessages.CustomMessage;
import us.ihmc.jros2.testmessages.CustomMessage2;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static us.ihmc.fastddsjava.fastddsjavaTools.retcodeThrowOnError;
import static us.ihmc.fastddsjava.pointers.fastddsjava.*;

public class CustomMessageTest
{
   static
   {
      fastddsjavaNativeLibrary.load();

      fastddsjava_load_xml_profiles_string("""
                                                 
                                                 <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                                                 <dds xmlns="http://www.eprosima.com">
                                                     <profiles>
                                                         <transport_descriptors>
                                                             <transport_descriptor>
                                                                 <transport_id>34d06f9b-3c96-4ebc-83bb-d7755af00d76</transport_id>
                                                                 <type>UDPv4</type>
                                                             </transport_descriptor>
                                                             <transport_descriptor>
                                                                 <transport_id>a1b9534d-b8a4-4fc2-b4a0-bab4c663b300</transport_id>
                                                                 <type>SHM</type>
                                                             </transport_descriptor>
                                                         </transport_descriptors>
                                                         <participant profile_name="34894fa6-90d6-4bec-b392-271c8e08e837">
                                                             <domainId>112</domainId>
                                                             <rtps>
                                                                 <name>test_node</name>
                                                                 <userTransports>
                                                                     <transport_id>34d06f9b-3c96-4ebc-83bb-d7755af00d76</transport_id>
                                                                     <transport_id>a1b9534d-b8a4-4fc2-b4a0-bab4c663b300</transport_id>
                                                                 </userTransports>
                                                                 <useBuiltinTransports>false</useBuiltinTransports>
                                                             </rtps>
                                                         </participant>
                                                        <topic profile_name="example_topic"/>
                                                        <data_writer profile_name="example_publisher">
                                                        </data_writer>
                                                        <data_reader profile_name="example_subscriber">
                                                        </data_reader>
                                                     </profiles>
                                                     <types/>
                                                     <log/>
                                                     <library_settings>
                                                         <intraprocess_delivery>FULL</intraprocess_delivery>
                                                     </library_settings>
                                                 </dds>
                                                 """.trim());
   }

   @Test
   public void testSendingBooleanMessage() throws fastddsjavaException, InterruptedException
   {
      int retCode;

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType(CustomMessage2.name, CDR_LE);
      Pointer typeSupport = fastddsjava_create_typesupport(topicDataWrapperType);

      Pointer participant = fastddsjava_create_participant("34894fa6-90d6-4bec-b392-271c8e08e837");

      retCode = fastddsjava_register_type(participant, typeSupport);
      retcodeThrowOnError(retCode);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "rt/ihmc/test_custom2", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_publisher");

      Pointer data = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(data);

      CustomMessage2 customMessage2 = new CustomMessage2();

      CustomMessage customMessage1 = new CustomMessage();
      customMessage1.setData(true);
      customMessage1.getIntList().add(44);

      customMessage2.getCustomMessageList().add(customMessage1);
      customMessage2.getCustomMessageList().add(customMessage1);

      ByteBuffer buffer = ByteBuffer.allocate(customMessage2.calculateSizeBytes() + 4);
      CDRBuffer cdrBuffer = new CDRBuffer(buffer);

      cdrBuffer.writePayloadHeader();
      customMessage2.serialize(cdrBuffer);

      topicDataWrapper.data_vector().resize(customMessage2.calculateSizeBytes() + 4);

      topicDataWrapper.data_ptr().put(buffer.array());

      int iter = 0;

      // Send the data
      while (iter < 100000)
      {
         retCode = fastddsjava_datawriter_write(dataWriter, topicDataWrapper);
         retcodeThrowOnError(retCode);

         System.out.println("wrote");

         iter++;

         Thread.sleep(1000);
      }

      // Delete / release all references
      topicDataWrapperType.delete_data(data);
      retcodeThrowOnError(fastddsjava_delete_datawriter(publisher, dataWriter));
      retcodeThrowOnError(fastddsjava_delete_publisher(participant, publisher));
      retcodeThrowOnError(fastddsjava_delete_topic(participant, topic));
      retcodeThrowOnError(fastddsjava_unregister_type(participant, topicDataWrapperType.get_name()));
      retcodeThrowOnError(fastddsjava_delete_participant(participant));
      assertTrue(topicDataWrapperType.releaseReference());
   }
}
