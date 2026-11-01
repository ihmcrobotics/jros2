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
package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import rcl_interfaces.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ROS2ParameterTest
{
   private static final long WAIT_MS = 10000;

   @Test
   public void testParameterValues()
   {
      assertEquals(ROS2ParameterType.INTEGER, ROS2Parameter.of("a", 3).getType());
      assertEquals(3L, ROS2Parameter.of("a", 3).asLong());
      assertEquals(ROS2ParameterType.DOUBLE, ROS2Parameter.of("a", 1.5f).getType());
      assertEquals(ROS2ParameterType.STRING_ARRAY, ROS2Parameter.of("a", List.of("x", "y")).getType());
      assertArrayEquals(new long[] {1, 2}, ROS2Parameter.of("a", new int[] {1, 2}).asLongArray());
      assertEquals(ROS2ParameterType.NOT_SET, ROS2Parameter.of("a", null).getType());
      assertThrows(IllegalArgumentException.class, () -> ROS2Parameter.of("a", new Object()));
      assertThrows(IllegalStateException.class, () -> ROS2Parameter.of("a", true).asLong());

      // Arrays are copied
      long[] values = {1, 2};
      ROS2Parameter parameter = ROS2Parameter.of("a", values);
      values[0] = 5;
      parameter.asLongArray()[1] = 5;
      assertArrayEquals(new long[] {1, 2}, parameter.asLongArray());

      // Round trip through rcl_interfaces/ParameterValue for every type
      Object[] samples = {true, 7L, 2.5, "text", new byte[] {1, 2}, new boolean[] {true, false}, new long[] {3, 4}, new double[] {0.5}, new String[] {"a", "b"}, null};
      for (Object sample : samples)
      {
         ROS2Parameter original = ROS2Parameter.of("p", sample);
         ParameterValue message = new ParameterValue();
         original.toMessage(message);
         assertEquals(original, ROS2Parameter.fromMessage("p", message), original.toString());
      }
   }

   @Test
   @Timeout(60)
   public void testLocalApi()
   {
      try (ROS2Node node = new ROS2Node("parameter_test_local"))
      {
         assertEquals(10L, node.declareParameter("rate", 10).asLong());
         node.declareParameter("frame", "map", new ROS2ParameterDescriptor("Frame id", true, false));
         node.declareParameter("dynamic", 1.0, new ROS2ParameterDescriptor("", false, true));
         assertThrows(IllegalStateException.class, () -> node.declareParameter("rate", 5));

         assertTrue(node.hasParameter("rate"));
         assertFalse(node.hasParameter("missing"));
         assertThrows(IllegalStateException.class, () -> node.getParameter("missing"));
         assertEquals("Frame id", node.describeParameter("frame").description());

         assertTrue(node.setParameter("rate", 20).successful());
         assertEquals(20L, node.getParameter("rate").asLong());

         ROS2SetParametersResult wrongType = node.setParameter("rate", "fast");
         assertFalse(wrongType.successful());
         assertEquals("Wrong parameter type, parameter {rate} is of type {integer}, setting it to {string} is not allowed.", wrongType.reason());
         assertEquals("parameter 'frame' cannot be set because it is read-only", node.setParameter("frame", "odom").reason());
         assertEquals("parameter 'missing' cannot be set because it was not declared", node.setParameter("missing", 1).reason());

         // Dynamic typing allows type changes and undeclaring
         assertTrue(node.setParameter("dynamic", "now a string").successful());
         node.undeclareParameter("dynamic");
         assertFalse(node.hasParameter("dynamic"));
         assertThrows(IllegalStateException.class, () -> node.undeclareParameter("rate"));

         // Callbacks can reject changes, also at declaration
         node.addOnSetParametersCallback(parameters ->
         {
            for (ROS2Parameter parameter : parameters)
            {
               if (parameter.getName().equals("rate") && parameter.asLong() <= 0)
                  return "rate must be positive";
            }
            return null;
         });
         assertEquals("rate must be positive", node.setParameter("rate", -1).reason());
         assertEquals(20L, node.getParameter("rate").asLong());

         // Atomic: nothing is applied if one fails
         ROS2SetParametersResult atomic = node.setParameters(List.of(ROS2Parameter.of("rate", 30), ROS2Parameter.of("frame", "x")));
         assertFalse(atomic.successful());
         assertEquals(20L, node.getParameter("rate").asLong());

         assertEquals(List.of("rate", "frame"), node.getParameters().stream().map(ROS2Parameter::getName).toList());
      }
   }

   @Test
   @Timeout(60)
   public void testParameterServices() throws Exception
   {
      try (ROS2Node node = new ROS2Node("parameter_test_server"); ROS2Node clientNode = new ROS2Node("parameter_test_client"))
      {
         node.declareParameter("rate", 10);
         node.declareParameter("control.gain.p", 1.5);
         node.declareParameter("control.gain.i", 0.1);
         node.declareParameter("control.mode", "auto");
         node.declareParameter("names", new String[] {"a", "b"}, new ROS2ParameterDescriptor("Some names", true, false));

         // list_parameters
         ROS2Client<ListParameters_Request, ListParameters_Response> list = clientNode.createClient("/parameter_test_server/list_parameters",
                                                                                                   ListParameters_Request.class,
                                                                                                   ListParameters_Response.class);
         assertTrue(list.waitForService(WAIT_MS));
         ListParameters_Response all = list.sendRequest(new ListParameters_Request()).get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertEquals(List.of("rate", "control.gain.p", "control.gain.i", "control.mode", "names"), toList(all.getResult().getNames()));
         assertEquals(List.of("control.gain", "control"), toList(all.getResult().getPrefixes()));

         ListParameters_Request controlDepth1 = new ListParameters_Request();
         controlDepth1.getPrefixes().add("control");
         controlDepth1.setDepth(1);
         assertEquals(List.of("control.mode"), toList(list.sendRequest(controlDepth1).get(WAIT_MS, TimeUnit.MILLISECONDS).getResult().getNames()));

         ListParameters_Request topLevel = new ListParameters_Request();
         topLevel.setDepth(1);
         assertEquals(List.of("rate", "names"), toList(list.sendRequest(topLevel).get(WAIT_MS, TimeUnit.MILLISECONDS).getResult().getNames()));

         // get_parameters, including an unknown name
         ROS2Client<GetParameters_Request, GetParameters_Response> get = clientNode.createClient("/parameter_test_server/get_parameters",
                                                                                                GetParameters_Request.class,
                                                                                                GetParameters_Response.class);
         assertTrue(get.waitForService(WAIT_MS));
         GetParameters_Request getRequest = new GetParameters_Request();
         getRequest.getNames().addAll(new String[] {"rate", "names", "unknown"});
         GetParameters_Response values = get.sendRequest(getRequest).get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertEquals(3, values.getValues().size());
         assertEquals(10L, ROS2Parameter.fromMessage("rate", values.getValues().get(0)).asLong());
         assertArrayEquals(new String[] {"a", "b"}, ROS2Parameter.fromMessage("names", values.getValues().get(1)).asStringArray());
         assertEquals(ROS2ParameterType.NOT_SET.getId(), values.getValues().get(2).getType());

         // get_parameter_types
         ROS2Client<GetParameterTypes_Request, GetParameterTypes_Response> types = clientNode.createClient("/parameter_test_server/get_parameter_types",
                                                                                                          GetParameterTypes_Request.class,
                                                                                                          GetParameterTypes_Response.class);
         assertTrue(types.waitForService(WAIT_MS));
         GetParameterTypes_Request typesRequest = new GetParameterTypes_Request();
         typesRequest.getNames().addAll(new String[] {"control.mode", "unknown"});
         GetParameterTypes_Response typesResponse = types.sendRequest(typesRequest).get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertEquals(ROS2ParameterType.STRING.getId(), typesResponse.getTypes().get(0));
         assertEquals(ROS2ParameterType.NOT_SET.getId(), typesResponse.getTypes().get(1));

         // set_parameters: each one on its own
         ROS2Client<SetParameters_Request, SetParameters_Response> set = clientNode.createClient("/parameter_test_server/set_parameters",
                                                                                                SetParameters_Request.class,
                                                                                                SetParameters_Response.class);
         assertTrue(set.waitForService(WAIT_MS));
         SetParameters_Request setRequest = new SetParameters_Request();
         ROS2Parameter.of("rate", 50).toMessage(setRequest.getParameters().add());
         ROS2Parameter.of("names", new String[] {"c"}).toMessage(setRequest.getParameters().add());
         SetParameters_Response setResponse = set.sendRequest(setRequest).get(WAIT_MS, TimeUnit.MILLISECONDS);
         assertTrue(setResponse.getResults().get(0).getSuccessful());
         assertFalse(setResponse.getResults().get(1).getSuccessful());
         assertEquals("parameter 'names' cannot be set because it is read-only", setResponse.getResults().get(1).getReasonAsString());
         assertEquals(50L, node.getParameter("rate").asLong());

         // set_parameters_atomically: all or nothing
         ROS2Client<SetParametersAtomically_Request, SetParametersAtomically_Response> setAtomically = clientNode.createClient(
               "/parameter_test_server/set_parameters_atomically",
               SetParametersAtomically_Request.class,
               SetParametersAtomically_Response.class);
         assertTrue(setAtomically.waitForService(WAIT_MS));
         SetParametersAtomically_Request atomicRequest = new SetParametersAtomically_Request();
         ROS2Parameter.of("control.gain.p", 2.0).toMessage(atomicRequest.getParameters().add());
         ROS2Parameter.of("control.gain.i", "wrong type").toMessage(atomicRequest.getParameters().add());
         assertFalse(setAtomically.sendRequest(atomicRequest).get(WAIT_MS, TimeUnit.MILLISECONDS).getResult().getSuccessful());
         assertEquals(1.5, node.getParameter("control.gain.p").asDouble());

         // describe_parameters
         ROS2Client<DescribeParameters_Request, DescribeParameters_Response> describe = clientNode.createClient(
               "/parameter_test_server/describe_parameters",
               DescribeParameters_Request.class,
               DescribeParameters_Response.class);
         assertTrue(describe.waitForService(WAIT_MS));
         DescribeParameters_Request describeRequest = new DescribeParameters_Request();
         describeRequest.getNames().add("names");
         ParameterDescriptor descriptor = describe.sendRequest(describeRequest).get(WAIT_MS, TimeUnit.MILLISECONDS).getDescriptors().get(0);
         assertEquals("names", descriptor.getNameAsString());
         assertEquals(ROS2ParameterType.STRING_ARRAY.getId(), descriptor.getType());
         assertEquals("Some names", descriptor.getDescriptionAsString());
         assertTrue(descriptor.getReadOnly());
      }
   }

   @Test
   @Timeout(60)
   public void testParameterEvents() throws Exception
   {
      try (ROS2Node listenerNode = new ROS2Node("parameter_test_listener"); ROS2Node node = new ROS2Node("parameter_test_events"))
      {
         List<ParameterEvent> events = new ArrayList<>();
         ROS2QoSProfile qos = new ROS2QoSProfile();
         qos.depth(1000);
         listenerNode.createSubscription(new ROS2Topic<>("/parameter_events", ParameterEvent.class, qos), reader ->
         {
            ParameterEvent event = new ParameterEvent();
            // Only the events about "speed"; the one for "first" may be lost while the publisher is matching
            if (reader.read(event) && event.getNodeAsString().equals("/parameter_test_events") && mentions(event, "speed"))
            {
               synchronized (events)
               {
                  events.add(event);
                  events.notifyAll();
               }
            }
         });

         // The first declaration creates the events publisher; wait for it to match before checking
         node.declareParameter("first", 1);
         assertTrue(node.getPublishers().get(0).waitForSubscription(WAIT_MS));
         node.declareParameter("speed", 1.0, new ROS2ParameterDescriptor("", false, true));
         node.setParameter("speed", 2.0);
         node.undeclareParameter("speed");

         synchronized (events)
         {
            long deadline = System.currentTimeMillis() + WAIT_MS;
            while (events.size() < 3 && System.currentTimeMillis() < deadline)
               events.wait(100);
         }
         assertEquals(3, events.size());
         List<ParameterEvent> last = events;
         assertEquals("speed", last.get(0).getNewParameters().get(0).getNameAsString());
         assertEquals(2.0, last.get(1).getChangedParameters().get(0).getValue().getDoubleValue());
         assertEquals("speed", last.get(2).getDeletedParameters().get(0).getNameAsString());
         assertTrue(last.get(0).getStamp().getSec() > 0);
      }
   }

   private static boolean mentions(ParameterEvent event, String name)
   {
      for (IDLObjectSequenceOfParameters parameters : new IDLObjectSequenceOfParameters[] {event::getNewParameters, event::getChangedParameters, event::getDeletedParameters})
      {
         for (Parameter parameter : parameters.get())
         {
            if (parameter.getNameAsString().equals(name))
               return true;
         }
      }
      return false;
   }

   private interface IDLObjectSequenceOfParameters
   {
      us.ihmc.fastddsjava.cdr.idl.IDLObjectSequence<Parameter> get();
   }

   private static List<String> toList(us.ihmc.fastddsjava.cdr.idl.IDLStringSequence sequence)
   {
      List<String> list = new ArrayList<>();
      for (int i = 0; i < sequence.size(); i++)
         list.add(sequence.getAsString(i));
      return list;
   }
}
