package us.ihmc.fastddsjava;

import org.bytedeco.javacpp.Pointer;
import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapper;
import us.ihmc.fastddsjava.pointers.fastddsjava_TopicDataWrapperType;
import us.ihmc.ros2.testmessages.Bool;

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
                                                                 <transport_id>0f4f3308-672c-4780-8dad-bf3f90f89490</transport_id>
                                                                 <type>SHM</type>
                                                             </transport_descriptor>
                                                             <transport_descriptor>
                                                                 <transport_id>71934648-74cb-46d0-a48d-738aecd823fd</transport_id>
                                                                 <type>UDPv4</type>
                                                             </transport_descriptor>
                                                         </transport_descriptors>
                                                         <participant profile_name="15549ef9-35af-40e3-a4f6-aa257fe31316">
                                                             <domainId>145</domainId>
                                                             <rtps>
                                                                 <name>test_node</name>
                                                                 <builtin>
                                                                     <discovery_config>
                                                                         <leaseDuration>
                                                                             <nanosec>-1</nanosec>
                                                                             <sec>2147483647</sec>
                                                                         </leaseDuration>
                                                                     </discovery_config>
                                                                 </builtin>
                                                                 <userTransports>
                                                                     <transport_id>0f4f3308-672c-4780-8dad-bf3f90f89490</transport_id>
                                                                     <transport_id>71934648-74cb-46d0-a48d-738aecd823fd</transport_id>
                                                                 </userTransports>
                                                                 <useBuiltinTransports>false</useBuiltinTransports>
                                                             </rtps>
                                                         </participant>
                                                        <topic profile_name="example_topic"/>
                                                        <data_writer profile_name="example_publisher"/>
                                                        <data_reader profile_name="example_subscriber"/>
                                                     </profiles>
                                                     <library_settings>
                                                         <intraprocess_delivery>OFF</intraprocess_delivery>
                                                     </library_settings>
                                                 </dds>
                                                 """.trim());
   }

   @Test
   public void testSendingBooleanMessage() throws fastddsjavaException, InterruptedException
   {
      int retCode;

      // Topic type
      fastddsjava_TopicDataWrapperType topicDataWrapperType = new fastddsjava_TopicDataWrapperType("std_msgs::msg::dds_::Bool_", CDR_LE);
      Pointer typeSupport = fastddsjava_create_typesupport(topicDataWrapperType);

      Pointer participant = fastddsjava_create_participant("15549ef9-35af-40e3-a4f6-aa257fe31316");

      retCode = fastddsjava_register_type(participant, typeSupport);
      retcodeThrowOnError(retCode);

      Pointer topic = fastddsjava_create_topic(participant, topicDataWrapperType, "rt/ihmc/test_bool", "example_topic");

      // Publisher
      Pointer publisher = fastddsjava_create_publisher(participant, "example_publisher");
      Pointer dataWriter = fastddsjava_create_datawriter(publisher, topic, "example_publisher");

      Pointer data = topicDataWrapperType.create_data();
      fastddsjava_TopicDataWrapper topicDataWrapper = new fastddsjava_TopicDataWrapper(data);

      Bool bool = new Bool();
      bool.setData_(true);

      bool.packTopicDataWrapper(topicDataWrapper, ByteBuffer.allocate(bool.calculateSize()));

      System.out.println(topicDataWrapper.data_vector().size());

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
