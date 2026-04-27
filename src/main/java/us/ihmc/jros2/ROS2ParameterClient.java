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

import rcl_interfaces.*;
import us.ihmc.fastddsjava.cdr.idl.IDLStringSequence;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for interacting with ROS 2 parameters on a remote node.
 * <p>
 * Parameters are configuration values that can be set and retrieved at runtime.
 * Every ROS 2 node provides parameter services for getting, setting, listing, and describing parameters.
 * <p>
 * Usage example:
 * <pre>{@code
 * ROS2ParameterClient paramClient = node.createParameterClient("my_node");
 *
 * // Set a parameter
 * ROS2Parameter param = new ROS2Parameter("max_speed", 10.0);
 * boolean success = paramClient.setParameter(param, 5000);
 *
 * // Get a parameter
 * ROS2Parameter retrieved = paramClient.getParameter("max_speed", 5000);
 * System.out.println("Max speed: " + retrieved.asDouble());
 *
 * // List all parameters
 * List<String> params = paramClient.listParameters(5000);
 *
 * paramClient.close();
 * }</pre>
 *
 * @see ROS2Parameter
 * @see ROS2Node#createParameterClient(String)
 * @see ROS2Node#destroyParameterClient(ROS2ParameterClient)
 */
public class ROS2ParameterClient implements Closeable
{
   private final String nodeName;
   private final ROS2Node node;
   private final ROS2ServiceClient<GetParameters_Request, GetParameters_Response> getParametersClient;
   private final ROS2ServiceClient<SetParameters_Request, SetParameters_Response> setParametersClient;
   private final ROS2ServiceClient<ListParameters_Request, ListParameters_Response> listParametersClient;
   private final ROS2ServiceClient<DescribeParameters_Request, DescribeParameters_Response> describeParametersClient;
   private final ROS2ServiceClient<GetParameterTypes_Request, GetParameterTypes_Response> getParameterTypesClient;

   ROS2ParameterClient(ROS2Node node, String nodeName, ROS2QoSProfile qosProfile)
   {
      this.node = node;
      this.nodeName = nodeName;
      String prefix = "/" + nodeName + "/";

      this.getParametersClient = node.createServiceClient(prefix + "get_parameters", GetParameters_Request.class, GetParameters_Response.class, qosProfile);
      this.setParametersClient = node.createServiceClient(prefix + "set_parameters", SetParameters_Request.class, SetParameters_Response.class, qosProfile);
      this.listParametersClient = node.createServiceClient(prefix + "list_parameters", ListParameters_Request.class, ListParameters_Response.class, qosProfile);
      this.describeParametersClient = node.createServiceClient(prefix + "describe_parameters",
                                                               DescribeParameters_Request.class,
                                                               DescribeParameters_Response.class,
                                                               qosProfile);
      this.getParameterTypesClient = node.createServiceClient(prefix + "get_parameter_types",
                                                              GetParameterTypes_Request.class,
                                                              GetParameterTypes_Response.class,
                                                              qosProfile);
   }

   /**
    * Wait for the parameter service server to become available.
    *
    * @param timeoutMs Maximum time to wait in milliseconds
    * @return true if server was discovered within timeout, false otherwise
    */
   public boolean waitForServer(long timeoutMs)
   {
      return getParametersClient.waitForServer(timeoutMs);
   }

   /**
    * Get a single parameter by name.
    *
    * @param name      The parameter name
    * @param timeoutMs Maximum time to wait for the response in milliseconds
    * @return The parameter, or null if not found or timeout
    */
   public ROS2Parameter getParameter(String name, long timeoutMs)
   {
      List<ROS2Parameter> params = getParameters(new String[] {name}, timeoutMs);
      return (params != null && !params.isEmpty()) ? params.get(0) : null;
   }

   /**
    * Get multiple parameters by name.
    *
    * @param names     Array of parameter names to get
    * @param timeoutMs Maximum time to wait for the response in milliseconds
    * @return List of parameters in the same order as requested, or null if timeout
    */
   public List<ROS2Parameter> getParameters(String[] names, long timeoutMs)
   {
      GetParameters_Request request = new GetParameters_Request();
      for (String name : names)
      {
         request.getNames().add(name);
      }

      GetParameters_Response response = getParametersClient.sendRequestSync(request, timeoutMs);
      if (response == null)
      {
         return null;
      }

      List<ROS2Parameter> result = new ArrayList<>();
      for (int i = 0; i < response.getValues().size(); i++)
      {
         ParameterValue value = response.getValues().get(i);
         String name = names[i];
         result.add(convertFromParameterValue(name, value));
      }
      return result;
   }

   /**
    * Set a single parameter.
    *
    * @param parameter The parameter to set
    * @param timeoutMs Maximum time to wait for the response in milliseconds
    * @return True if the parameter was set successfully, false otherwise
    */
   public boolean setParameter(ROS2Parameter parameter, long timeoutMs)
   {
      List<Boolean> results = setParameters(new ROS2Parameter[] {parameter}, timeoutMs);
      return results != null && !results.isEmpty() && results.get(0);
   }

   /**
    * Set multiple parameters.
    *
    * @param parameters Array of parameters to set
    * @param timeoutMs  Maximum time to wait for the response in milliseconds
    * @return List of booleans indicating success for each parameter, or null if timeout
    */
   public List<Boolean> setParameters(ROS2Parameter[] parameters, long timeoutMs)
   {
      SetParameters_Request request = new SetParameters_Request();
      for (ROS2Parameter param : parameters)
      {
         Parameter rosParam = convertToParameter(param);
         request.getParameters().add(rosParam);
      }

      SetParameters_Response response = setParametersClient.sendRequestSync(request, timeoutMs);
      if (response == null)
      {
         return null;
      }

      List<Boolean> results = new ArrayList<>();
      for (int i = 0; i < response.getResults().size(); i++)
      {
         results.add(response.getResults().get(i).getSuccessful());
      }
      return results;
   }

   /**
    * List all parameters on the node.
    *
    * @param timeoutMs Maximum time to wait for the response in milliseconds
    * @return List of parameter names, or null if timeout
    */
   public List<String> listParameters(long timeoutMs)
   {
      return listParameters(new String[] {}, ListParameters_Request.DEPTH_RECURSIVE, timeoutMs);
   }

   /**
    * List parameters matching the given prefixes and depth.
    *
    * @param prefixes  Array of parameter name prefixes to match
    * @param depth     Recursion depth (use DEPTH_RECURSIVE for unlimited)
    * @param timeoutMs Maximum time to wait for the response in milliseconds
    * @return List of parameter names, or null if timeout
    */
   public List<String> listParameters(String[] prefixes, long depth, long timeoutMs)
   {
      ListParameters_Request request = new ListParameters_Request();
      for (String prefix : prefixes)
      {
         request.getPrefixes().add(prefix);
      }
      request.setDepth(depth);

      ListParameters_Response response = listParametersClient.sendRequestSync(request, timeoutMs);
      if (response == null)
      {
         return null;
      }

      List<String> result = new ArrayList<>();
      IDLStringSequence names = response.getResult().getNames();
      for (int i = 0; i < names.size(); i++)
      {
         result.add(names.get(i).toString());
      }
      return result;
   }

   /**
    * Get the types of multiple parameters.
    *
    * @param names     Array of parameter names
    * @param timeoutMs Maximum time to wait for the response in milliseconds
    * @return List of parameter type bytes, or null if timeout
    */
   public List<Short> getParameterTypes(String[] names, long timeoutMs)
   {
      GetParameterTypes_Request request = new GetParameterTypes_Request();
      for (String name : names)
      {
         request.getNames().add(name);
      }

      GetParameterTypes_Response response = getParameterTypesClient.sendRequestSync(request, timeoutMs);
      if (response == null)
      {
         return null;
      }

      List<Short> result = new ArrayList<>();
      for (int i = 0; i < response.getTypes().size(); i++)
      {
         result.add(response.getTypes().get(i));
      }
      return result;
   }

   /**
    * Describe parameters to get their descriptors.
    *
    * @param names     Array of parameter names
    * @param timeoutMs Maximum time to wait for the response in milliseconds
    * @return List of parameter descriptors, or null if timeout
    */
   public List<ParameterDescriptor> describeParameters(String[] names, long timeoutMs)
   {
      DescribeParameters_Request request = new DescribeParameters_Request();
      for (String name : names)
      {
         request.getNames().add(name);
      }

      DescribeParameters_Response response = describeParametersClient.sendRequestSync(request, timeoutMs);
      if (response == null)
      {
         return null;
      }

      List<ParameterDescriptor> result = new ArrayList<>();
      for (int i = 0; i < response.getDescriptors().size(); i++)
      {
         result.add(response.getDescriptors().get(i));
      }
      return result;
   }

   /**
    * Get the node name this parameter client is connected to.
    *
    * @return The node name
    */
   public String getNodeName()
   {
      return nodeName;
   }

   /**
    * Subscribe to parameter events for this node.
    * This allows monitoring parameter changes on the remote node.
    *
    * @param node     The ROS2Node to create the subscription on
    * @param callback The callback to invoke when parameter events are received
    * @return The parameter event subscription
    */
   public ROS2Subscription<ParameterEvent> onParameterEvent(ROS2Node node, ROS2ParameterEventCallback callback)
   {
      return onParameterEvent(node, callback, ROS2QoSProfile.PARAMETER_EVENTS);
   }

   /**
    * Subscribe to parameter events for this node with a custom QoS profile.
    *
    * @param node       The ROS2Node to create the subscription on
    * @param callback   The callback to invoke when parameter events are received
    * @param qosProfile The QoS profile for the subscription
    * @return The parameter event subscription
    */
   public ROS2Subscription<ParameterEvent> onParameterEvent(ROS2Node node, ROS2ParameterEventCallback callback, ROS2QoSProfile qosProfile)
   {
      ROS2Topic<ParameterEvent> eventTopic = new ROS2Topic<>("/parameter_events", ParameterEvent.class);
      return node.createSubscription(eventTopic, (eventReader) ->
      {
         ParameterEvent event = new ParameterEvent();
         eventReader.read(event);

         // Only invoke callback if the event is for our target node
         if (event.getNodeAsString().equals("/" + nodeName) || event.getNodeAsString().equals(nodeName))
         {
            callback.onParameterEvent(event);
         }
      }, qosProfile);
   }

   @Override
   public void close()
   {
      // Properly destroy service clients through the node
      node.destroyServiceClient(getParametersClient);
      node.destroyServiceClient(setParametersClient);
      node.destroyServiceClient(listParametersClient);
      node.destroyServiceClient(describeParametersClient);
      node.destroyServiceClient(getParameterTypesClient);
   }

   /**
    * Convert ROS2Parameter to rcl_interfaces Parameter message.
    */
   private Parameter convertToParameter(ROS2Parameter param)
   {
      Parameter rosParam = new Parameter();
      rosParam.setName(param.getName());

      ParameterValue value = rosParam.getValue();
      switch (param.getType())
      {
         case PARAMETER_NOT_SET:
            value.setType(ParameterType.PARAMETER_NOT_SET);
            break;
         case PARAMETER_BOOL:
            value.setType(ParameterType.PARAMETER_BOOL);
            value.setBoolValue(param.asBool());
            break;
         case PARAMETER_INTEGER:
            value.setType(ParameterType.PARAMETER_INTEGER);
            value.setIntegerValue(param.asLong());
            break;
         case PARAMETER_DOUBLE:
            value.setType(ParameterType.PARAMETER_DOUBLE);
            value.setDoubleValue(param.asDouble());
            break;
         case PARAMETER_STRING:
            value.setType(ParameterType.PARAMETER_STRING);
            value.setStringValue(param.asString());
            break;
         case PARAMETER_BYTE_ARRAY:
            value.setType(ParameterType.PARAMETER_BYTE_ARRAY);
            for (byte b : param.asByteArray())
            {
               value.getByteArrayValue().add(b);
            }
            break;
         case PARAMETER_BOOL_ARRAY:
            value.setType(ParameterType.PARAMETER_BOOL_ARRAY);
            for (boolean b : param.asBoolArray())
            {
               value.getBoolArrayValue().add(b);
            }
            break;
         case PARAMETER_INTEGER_ARRAY:
            value.setType(ParameterType.PARAMETER_INTEGER_ARRAY);
            for (long l : param.asLongArray())
            {
               value.getIntegerArrayValue().add(l);
            }
            break;
         case PARAMETER_DOUBLE_ARRAY:
            value.setType(ParameterType.PARAMETER_DOUBLE_ARRAY);
            for (double d : param.asDoubleArray())
            {
               value.getDoubleArrayValue().add(d);
            }
            break;
         case PARAMETER_STRING_ARRAY:
            value.setType(ParameterType.PARAMETER_STRING_ARRAY);
            for (String s : param.asStringArray())
            {
               value.getStringArrayValue().add(s);
            }
            break;
      }
      return rosParam;
   }

   /**
    * Convert rcl_interfaces ParameterValue to ROS2Parameter.
    */
   private ROS2Parameter convertFromParameterValue(String name, ParameterValue value)
   {
      short type = value.getType();
      if (type == ParameterType.PARAMETER_NOT_SET)
      {
         return new ROS2Parameter(name);
      }
      else if (type == ParameterType.PARAMETER_BOOL)
      {
         return new ROS2Parameter(name, value.getBoolValue());
      }
      else if (type == ParameterType.PARAMETER_INTEGER)
      {
         return new ROS2Parameter(name, value.getIntegerValue());
      }
      else if (type == ParameterType.PARAMETER_DOUBLE)
      {
         return new ROS2Parameter(name, value.getDoubleValue());
      }
      else if (type == ParameterType.PARAMETER_STRING)
      {
         return new ROS2Parameter(name, value.getStringValueAsString());
      }
      else if (type == ParameterType.PARAMETER_BYTE_ARRAY)
      {
         byte[] arr = new byte[value.getByteArrayValue().size()];
         for (int i = 0; i < arr.length; i++)
         {
            arr[i] = value.getByteArrayValue().get(i);
         }
         return new ROS2Parameter(name, arr);
      }
      else if (type == ParameterType.PARAMETER_BOOL_ARRAY)
      {
         boolean[] arr = new boolean[value.getBoolArrayValue().size()];
         for (int i = 0; i < arr.length; i++)
         {
            arr[i] = value.getBoolArrayValue().get(i);
         }
         return new ROS2Parameter(name, arr);
      }
      else if (type == ParameterType.PARAMETER_INTEGER_ARRAY)
      {
         long[] arr = new long[value.getIntegerArrayValue().size()];
         for (int i = 0; i < arr.length; i++)
         {
            arr[i] = value.getIntegerArrayValue().get(i);
         }
         return new ROS2Parameter(name, arr);
      }
      else if (type == ParameterType.PARAMETER_DOUBLE_ARRAY)
      {
         double[] arr = new double[value.getDoubleArrayValue().size()];
         for (int i = 0; i < arr.length; i++)
         {
            arr[i] = value.getDoubleArrayValue().get(i);
         }
         return new ROS2Parameter(name, arr);
      }
      else if (type == ParameterType.PARAMETER_STRING_ARRAY)
      {
         String[] arr = new String[value.getStringArrayValue().size()];
         for (int i = 0; i < arr.length; i++)
         {
            arr[i] = value.getStringArrayValue().get(i).toString();
         }
         return new ROS2Parameter(name, arr);
      }
      else
      {
         return new ROS2Parameter(name);
      }
   }
}
