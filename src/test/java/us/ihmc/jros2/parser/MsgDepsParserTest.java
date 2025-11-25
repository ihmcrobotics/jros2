/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2.parser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.jros2.parser.field.InterfaceField;
import us.ihmc.jros2.parser.field.InterfaceFieldParsingException;
import us.ihmc.jros2.parser.msgdeps.MsgDepsContext;
import us.ihmc.jros2.parser.msgdeps.MsgDepsParser;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

public class MsgDepsParserTest
{
   @Test
   public void testMsgDepsParse() throws URISyntaxException, IOException, InterfaceFieldParsingException
   {
      String schema = new String(Files.readAllBytes(Paths.get(Objects.requireNonNull(MsgDepsParserTest.class.getClassLoader().getResource("test.msgdeps"))
                                                                     .toURI())));

      MsgDepsContext msgDepsContext = MsgDepsParser.parseMsgDeps(schema, "test_pkg/Test");

      Assertions.assertEquals("test_pkg/Test", msgDepsContext.getPackageResourceName());
      Assertions.assertEquals("test_pkg", msgDepsContext.getPackageName());
      Assertions.assertEquals("Test", msgDepsContext.getResourceName());
      Assertions.assertEquals("test_pkg.msg.dds", msgDepsContext.getJavaPackageName());
      Assertions.assertEquals("Test", msgDepsContext.getJavaClassName());

      Assertions.assertEquals(2, msgDepsContext.getDependencies().size());

      Assertions.assertTrue(msgDepsContext.getDependencies().containsKey("builtin_interfaces/Time"));
      Assertions.assertTrue(msgDepsContext.getDependencies().containsKey("hardware_msgs/ActuatorCommand"));

      MsgContext builtin_interfaces_Time = msgDepsContext.getDependencies().get("builtin_interfaces/Time");
      MsgContext hardware_msgs_ActuatorCommand = msgDepsContext.getDependencies().get("hardware_msgs/ActuatorCommand");

      // Assertions for builtin_interfaces/Time
      Assertions.assertEquals("builtin_interfaces/Time", builtin_interfaces_Time.getPackageResourceName());
      Assertions.assertEquals("builtin_interfaces", builtin_interfaces_Time.getPackageName());
      Assertions.assertEquals("Time", builtin_interfaces_Time.getResourceName());
      Assertions.assertEquals("builtin_interfaces.msg.dds", builtin_interfaces_Time.getJavaPackageName());
      Assertions.assertEquals("Time", builtin_interfaces_Time.getJavaClassName());
      Assertions.assertEquals("This message communicates ROS Time defined here:\nhttps://design.ros2.org/articles/clock_and_time.html",
                              builtin_interfaces_Time.getHeaderComment());
      Assertions.assertEquals(2, builtin_interfaces_Time.getFields().size());
      InterfaceField Time_sec_field = builtin_interfaces_Time.getFields().get("sec");
      {
         Assertions.assertEquals("int32", Time_sec_field.getType());
         Assertions.assertEquals("int", Time_sec_field.getJavaType());
         Assertions.assertFalse(Time_sec_field.isObjectSequence());
         Assertions.assertNull(Time_sec_field.getObjectSequenceTypeClass());
         Assertions.assertEquals("sec", Time_sec_field.getName());
         Assertions.assertEquals("sec_", Time_sec_field.getJavaName());
         Assertions.assertEquals("getSec", Time_sec_field.getJavaGetterMethodName());
         Assertions.assertEquals("setSec", Time_sec_field.getJavaSetterMethodName());
         Assertions.assertFalse(Time_sec_field.isStringUpperBounded());
         Assertions.assertEquals(0, Time_sec_field.getStringLength());
         Assertions.assertFalse(Time_sec_field.hasStringMaxLength());
         Assertions.assertFalse(Time_sec_field.isArray());
         Assertions.assertFalse(Time_sec_field.isUpperBounded());
         Assertions.assertFalse(Time_sec_field.isUnbounded());
         Assertions.assertFalse(Time_sec_field.isSequence());
         Assertions.assertFalse(Time_sec_field.isFixedSize());
         Assertions.assertEquals(-1, Time_sec_field.getLength());
         Assertions.assertNull(Time_sec_field.getConstantValue());
         Assertions.assertNull(Time_sec_field.getDefaultValue());
         Assertions.assertFalse(Time_sec_field.isDefaultValueArray());
         Assertions.assertEquals(0, Time_sec_field.getDefaultValueArrayValues().size());
         Assertions.assertEquals("The seconds component, valid over all int32 values.", Time_sec_field.getHeaderComment());
         Assertions.assertNull(Time_sec_field.getTrailingComment());
         Assertions.assertTrue(Time_sec_field.isBuiltinType());
         Assertions.assertFalse(Time_sec_field.isBuiltinStringType());
         Assertions.assertFalse(Time_sec_field.isBuiltinWStringType());
         Assertions.assertEquals(4, Time_sec_field.getBuiltinTypeSize());
         Assertions.assertEquals("int", Time_sec_field.getBuiltinTypeJavaType());
         Assertions.assertEquals("IDLIntSequence", Time_sec_field.getBuiltinTypeIDLSequenceType());
         Assertions.assertEquals("writeInt", Time_sec_field.getBuiltinCDRBufferWriteMethod());
         Assertions.assertEquals("readInt", Time_sec_field.getBuiltinCDRBufferReadMethod());
      }
      InterfaceField Time_nanosec_field = builtin_interfaces_Time.getFields().get("nanosec");
      {
         Assertions.assertEquals("uint32", Time_nanosec_field.getType());
         Assertions.assertEquals("int", Time_nanosec_field.getJavaType());
         Assertions.assertFalse(Time_nanosec_field.isObjectSequence());
         Assertions.assertNull(Time_nanosec_field.getObjectSequenceTypeClass());
         Assertions.assertEquals("nanosec", Time_nanosec_field.getName());
         Assertions.assertEquals("nanosec_", Time_nanosec_field.getJavaName());
         Assertions.assertEquals("getNanosec", Time_nanosec_field.getJavaGetterMethodName());
         Assertions.assertEquals("setNanosec", Time_nanosec_field.getJavaSetterMethodName());
         Assertions.assertFalse(Time_nanosec_field.isStringUpperBounded());
         Assertions.assertEquals(0, Time_nanosec_field.getStringLength());
         Assertions.assertFalse(Time_nanosec_field.hasStringMaxLength());
         Assertions.assertFalse(Time_nanosec_field.isArray());
         Assertions.assertFalse(Time_nanosec_field.isUpperBounded());
         Assertions.assertFalse(Time_nanosec_field.isUnbounded());
         Assertions.assertFalse(Time_nanosec_field.isSequence());
         Assertions.assertFalse(Time_nanosec_field.isFixedSize());
         Assertions.assertEquals(-1, Time_nanosec_field.getLength());
         Assertions.assertNull(Time_nanosec_field.getConstantValue());
         Assertions.assertNull(Time_nanosec_field.getDefaultValue());
         Assertions.assertFalse(Time_nanosec_field.isDefaultValueArray());
         Assertions.assertEquals(0, Time_nanosec_field.getDefaultValueArrayValues().size());
         Assertions.assertEquals("The nanoseconds component, valid in the range [0, 10e9).", Time_nanosec_field.getHeaderComment());
         Assertions.assertNull(Time_nanosec_field.getTrailingComment());
         Assertions.assertTrue(Time_nanosec_field.isBuiltinType());
         Assertions.assertFalse(Time_nanosec_field.isBuiltinStringType());
         Assertions.assertFalse(Time_nanosec_field.isBuiltinWStringType());
         Assertions.assertEquals(4, Time_nanosec_field.getBuiltinTypeSize());
         Assertions.assertEquals("int", Time_nanosec_field.getBuiltinTypeJavaType());
         Assertions.assertEquals("IDLIntSequence", Time_nanosec_field.getBuiltinTypeIDLSequenceType());
         Assertions.assertEquals("writeInt", Time_nanosec_field.getBuiltinCDRBufferWriteMethod());
         Assertions.assertEquals("readInt", Time_nanosec_field.getBuiltinCDRBufferReadMethod());
      }
   }
}
