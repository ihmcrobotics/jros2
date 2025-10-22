package us.ihmc.jros2.parser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import us.ihmc.jros2.parser.field.InterfaceField;
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
   public void testMsgDepsParse() throws URISyntaxException, IOException
   {
      String schema = new String(Files.readAllBytes(Paths.get(Objects.requireNonNull(MsgDepsParserTest.class.getClassLoader().getResource("test.msgdeps"))
                                                                     .toURI())));

      MsgDepsContext msgDepsContext = MsgDepsParser.parseMsgDeps(schema, "test_pkg/Test");

      Assertions.assertEquals("test_pkg/Test", msgDepsContext.getMeta().getPackageResourceName());
      Assertions.assertEquals("test_pkg", msgDepsContext.getMeta().getPackageName());
      Assertions.assertEquals("Test", msgDepsContext.getMeta().getResourceName());
      Assertions.assertEquals("test_pkg.msg.dds", msgDepsContext.getMeta().getJavaPackageName());
      Assertions.assertEquals("Test", msgDepsContext.getMeta().getJavaClassName());

      Assertions.assertEquals(2, msgDepsContext.getDependencies().size());

      Assertions.assertTrue(msgDepsContext.getDependencies().containsKey("builtin_interfaces/Time"));
      Assertions.assertTrue(msgDepsContext.getDependencies().containsKey("hardware_msgs/ActuatorCommand"));

      MsgContext builtin_interfaces_Time = msgDepsContext.getDependencies().get("builtin_interfaces/Time");
      MsgContext hardware_msgs_ActuatorCommand = msgDepsContext.getDependencies().get("hardware_msgs/ActuatorCommand");

      // Assertions for builtin_interfaces/Time
      Assertions.assertEquals("builtin_interfaces/Time", builtin_interfaces_Time.getMeta().getPackageResourceName());
      Assertions.assertEquals("builtin_interfaces", builtin_interfaces_Time.getMeta().getPackageName());
      Assertions.assertEquals("Time", builtin_interfaces_Time.getMeta().getResourceName());
      Assertions.assertEquals("builtin_interfaces.msg.dds", builtin_interfaces_Time.getMeta().getJavaPackageName());
      Assertions.assertEquals("Time", builtin_interfaces_Time.getMeta().getJavaClassName());
      Assertions.assertEquals("# This message communicates ROS Time defined here:\n# https://design.ros2.org/articles/clock_and_time.html",
                              builtin_interfaces_Time.getMeta().getHeaderComment());
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
         Assertions.assertEquals(-1, Time_sec_field.getLength()); // TODO: Should be 0?
         Assertions.assertNull(Time_sec_field.getConstantValue());
         Assertions.assertNull(Time_sec_field.getDefaultValue());
         Assertions.assertFalse(Time_sec_field.isDefaultValueArray());
         Assertions.assertEquals(0, Time_sec_field.getDefaultValueArrayValues().size());
         Assertions.assertEquals("# The seconds component, valid over all int32 values.", Time_sec_field.getHeaderComment());
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
         Assertions.assertEquals("long", Time_nanosec_field.getJavaType());
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
         Assertions.assertEquals(-1, Time_nanosec_field.getLength()); // TODO: Should be 0?
         Assertions.assertNull(Time_nanosec_field.getConstantValue());
         Assertions.assertNull(Time_nanosec_field.getDefaultValue());
         Assertions.assertFalse(Time_nanosec_field.isDefaultValueArray());
         Assertions.assertEquals(0, Time_nanosec_field.getDefaultValueArrayValues().size());
         Assertions.assertEquals("# The nanoseconds component, valid in the range [0, 10e9).", Time_nanosec_field.getHeaderComment());
         Assertions.assertNull(Time_nanosec_field.getTrailingComment());
         Assertions.assertTrue(Time_nanosec_field.isBuiltinType());
         Assertions.assertFalse(Time_nanosec_field.isBuiltinStringType());
         Assertions.assertFalse(Time_nanosec_field.isBuiltinWStringType());
         Assertions.assertEquals(8, Time_nanosec_field.getBuiltinTypeSize());
         Assertions.assertEquals("long", Time_nanosec_field.getBuiltinTypeJavaType());
         Assertions.assertEquals("IDLLongSequence", Time_nanosec_field.getBuiltinTypeIDLSequenceType());
         Assertions.assertEquals("writeLong", Time_nanosec_field.getBuiltinCDRBufferWriteMethod());
         Assertions.assertEquals("readLong", Time_nanosec_field.getBuiltinCDRBufferReadMethod());
      }
   }
}
