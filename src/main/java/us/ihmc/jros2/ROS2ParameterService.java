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

import org.bytedeco.javacpp.Pointer;
import rcl_interfaces.*;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages the six ROS 2 parameter services for a node.
 * <p>
 * Automatically creates the following services:
 * <ul>
 *    <li>/{node_name}/get_parameters</li>
 *    <li>/{node_name}/set_parameters</li>
 *    <li>/{node_name}/set_parameters_atomically</li>
 *    <li>/{node_name}/list_parameters</li>
 *    <li>/{node_name}/describe_parameters</li>
 *    <li>/{node_name}/get_parameter_types</li>
 * </ul>
 * <p>
 * These services enable remote parameter access from other ROS 2 nodes.
 */
class ROS2ParameterService implements Closeable
{
   private final ROS2Node node;
   private final ROS2ServiceServer<GetParameters_Request, GetParameters_Response> getParametersServer;
   private final ROS2ServiceServer<SetParameters_Request, SetParameters_Response> setParametersServer;
   private final ROS2ServiceServer<SetParametersAtomically_Request, SetParametersAtomically_Response> setParametersAtomicallyServer;
   private final ROS2ServiceServer<ListParameters_Request, ListParameters_Response> listParametersServer;
   private final ROS2ServiceServer<DescribeParameters_Request, DescribeParameters_Response> describeParametersServer;
   private final ROS2ServiceServer<GetParameterTypes_Request, GetParameterTypes_Response> getParameterTypesServer;

   /**
    * Creates parameter services for the given node.
    *
    * @param node The ROS 2 node to create parameter services for
    */
   ROS2ParameterService(ROS2Node node)
   {
      this.node = node;
      String nodeName = node.getName();

      // Create all 6 parameter services
      getParametersServer = node.createServiceServer("/" + nodeName + "/get_parameters",
                                                      GetParameters_Request.class,
                                                      GetParameters_Response.class,
                                                      this::handleGetParameters);

      setParametersServer = node.createServiceServer("/" + nodeName + "/set_parameters",
                                                      SetParameters_Request.class,
                                                      SetParameters_Response.class,
                                                      this::handleSetParameters);

      setParametersAtomicallyServer = node.createServiceServer("/" + nodeName + "/set_parameters_atomically",
                                                                SetParametersAtomically_Request.class,
                                                                SetParametersAtomically_Response.class,
                                                                this::handleSetParametersAtomically);

      listParametersServer = node.createServiceServer("/" + nodeName + "/list_parameters",
                                                       ListParameters_Request.class,
                                                       ListParameters_Response.class,
                                                       this::handleListParameters);

      describeParametersServer = node.createServiceServer("/" + nodeName + "/describe_parameters",
                                                           DescribeParameters_Request.class,
                                                           DescribeParameters_Response.class,
                                                           this::handleDescribeParameters);

      getParameterTypesServer = node.createServiceServer("/" + nodeName + "/get_parameter_types",
                                                          GetParameterTypes_Request.class,
                                                          GetParameterTypes_Response.class,
                                                          this::handleGetParameterTypes);
   }

   /**
    * Handle get_parameters service requests.
    */
   private void handleGetParameters(GetParameters_Request request, GetParameters_Response response)
   {
      for (int i = 0; i < request.getNames().size(); i++)
      {
         String paramName = request.getNames().get(i).toString();
         ROS2Parameter param = node.getParameter(paramName);

         ParameterValue value = new ParameterValue();
         if (param != null)
         {
            convertToParameterValue(param, value);
         }
         else
         {
            // Parameter not found - return PARAMETER_NOT_SET
            value.setType((byte) 0);
         }
         response.getValues().add(value);
      }
   }

   /**
    * Handle set_parameters service requests.
    */
   private void handleSetParameters(SetParameters_Request request, SetParameters_Response response)
   {
      for (int i = 0; i < request.getParameters().size(); i++)
      {
         Parameter param = request.getParameters().get(i);
         SetParametersResult result = new SetParametersResult();

         try
         {
            ROS2Parameter ros2Param = convertFromParameter(param);
            boolean success = node.setParameter(ros2Param);
            result.setSuccessful(success);
            if (!success)
            {
               result.setReason("Failed to set parameter");
            }
         }
         catch (Exception e)
         {
            result.setSuccessful(false);
            result.setReason("Error setting parameter: " + e.getMessage());
         }

         response.getResults().add(result);
      }
   }

   /**
    * Handle set_parameters_atomically service requests.
    * All parameters must be set successfully, or none are set (rollback).
    */
   private void handleSetParametersAtomically(SetParametersAtomically_Request request, SetParametersAtomically_Response response)
   {
      List<ROS2Parameter> convertedParams = new ArrayList<>();
      List<String> originalNames = new ArrayList<>();
      List<ROS2Parameter> originalValues = new ArrayList<>();

      // First, convert all parameters and save original values for rollback
      try
      {
         for (int i = 0; i < request.getParameters().size(); i++)
         {
            Parameter param = request.getParameters().get(i);
            ROS2Parameter ros2Param = convertFromParameter(param);
            convertedParams.add(ros2Param);

            // Save original value for potential rollback
            originalNames.add(ros2Param.getName());
            ROS2Parameter original = node.getParameter(ros2Param.getName());
            originalValues.add(original);
         }

         // Try to set all parameters
         boolean allSuccess = true;
         for (ROS2Parameter param : convertedParams)
         {
            if (!node.setParameter(param))
            {
               allSuccess = false;
               break;
            }
         }

         if (allSuccess)
         {
            response.getResult().setSuccessful(true);
         }
         else
         {
            // Rollback - restore original values
            for (int i = 0; i < originalNames.size(); i++)
            {
               if (originalValues.get(i) != null)
               {
                  node.setParameter(originalValues.get(i));
               }
            }
            response.getResult().setSuccessful(false);
            response.getResult().setReason("Atomic operation failed - all changes rolled back");
         }
      }
      catch (Exception e)
      {
         // Rollback on exception
         for (int i = 0; i < originalNames.size(); i++)
         {
            if (originalValues.get(i) != null)
            {
               node.setParameter(originalValues.get(i));
            }
         }
         response.getResult().setSuccessful(false);
         response.getResult().setReason("Error during atomic operation: " + e.getMessage());
      }
   }

   /**
    * Handle list_parameters service requests.
    */
   private void handleListParameters(ListParameters_Request request, ListParameters_Response response)
   {
      Map<String, ROS2Parameter> allParams = node.getParameters();
      long depth = request.getDepth();

      // Get prefixes to filter by
      List<String> prefixes = new ArrayList<>();
      for (int i = 0; i < request.getPrefixes().size(); i++)
      {
         prefixes.add(request.getPrefixes().get(i).toString());
      }

      for (String paramName : allParams.keySet())
      {
         // Filter by prefixes if provided
         boolean matchesPrefix = prefixes.isEmpty();
         for (String prefix : prefixes)
         {
            if (paramName.startsWith(prefix))
            {
               matchesPrefix = true;
               break;
            }
         }

         if (matchesPrefix)
         {
            response.getResult().getNames().add(paramName);
         }
      }
   }

   /**
    * Handle describe_parameters service requests.
    */
   private void handleDescribeParameters(DescribeParameters_Request request, DescribeParameters_Response response)
   {
      for (int i = 0; i < request.getNames().size(); i++)
      {
         String paramName = request.getNames().get(i).toString();
         ROS2Parameter param = node.getParameter(paramName);

         ParameterDescriptor descriptor = new ParameterDescriptor();
         descriptor.setName(paramName);

         if (param != null)
         {
            descriptor.setType(getParameterTypeCode(param.getType()));
         }
         else
         {
            descriptor.setType((byte) 0); // PARAMETER_NOT_SET
         }

         response.getDescriptors().add(descriptor);
      }
   }

   /**
    * Handle get_parameter_types service requests.
    */
   private void handleGetParameterTypes(GetParameterTypes_Request request, GetParameterTypes_Response response)
   {
      for (int i = 0; i < request.getNames().size(); i++)
      {
         String paramName = request.getNames().get(i).toString();
         ROS2Parameter param = node.getParameter(paramName);

         byte typeCode;
         if (param != null)
         {
            typeCode = getParameterTypeCode(param.getType());
         }
         else
         {
            typeCode = 0; // PARAMETER_NOT_SET
         }

         response.getTypes().add(typeCode);
      }
   }

   /**
    * Convert ROS2Parameter to ParameterValue.
    */
   private void convertToParameterValue(ROS2Parameter param, ParameterValue value)
   {
      value.setType(getParameterTypeCode(param.getType()));

      switch (param.getType())
      {
         case PARAMETER_BOOL:
            value.setBoolValue(param.asBool());
            break;
         case PARAMETER_INTEGER:
            value.setIntegerValue(param.asLong());
            break;
         case PARAMETER_DOUBLE:
            value.setDoubleValue(param.asDouble());
            break;
         case PARAMETER_STRING:
            value.setStringValue(param.asString());
            break;
         case PARAMETER_BYTE_ARRAY:
            byte[] bytes = param.asByteArray();
            for (byte b : bytes)
            {
               value.getByteArrayValue().add(b);
            }
            break;
         case PARAMETER_BOOL_ARRAY:
            boolean[] bools = param.asBoolArray();
            for (boolean b : bools)
            {
               value.getBoolArrayValue().add(b);
            }
            break;
         case PARAMETER_INTEGER_ARRAY:
            long[] longs = param.asLongArray();
            for (long l : longs)
            {
               value.getIntegerArrayValue().add(l);
            }
            break;
         case PARAMETER_DOUBLE_ARRAY:
            double[] doubles = param.asDoubleArray();
            for (double d : doubles)
            {
               value.getDoubleArrayValue().add(d);
            }
            break;
         case PARAMETER_STRING_ARRAY:
            String[] strings = param.asStringArray();
            for (String s : strings)
            {
               value.getStringArrayValue().add(s);
            }
            break;
         case PARAMETER_NOT_SET:
            // Already set type to 0
            break;
      }
   }

   /**
    * Convert Parameter to ROS2Parameter.
    */
   private ROS2Parameter convertFromParameter(Parameter param)
   {
      String name = param.getNameAsString();
      ParameterValue value = param.getValue();
      short type = value.getType();

      // Type codes: 0=NOT_SET, 1=BOOL, 2=INTEGER, 3=DOUBLE, 4=STRING, 5=BYTE_ARRAY,
      //             6=BOOL_ARRAY, 7=INTEGER_ARRAY, 8=DOUBLE_ARRAY, 9=STRING_ARRAY
      switch (type)
      {
         case 1: // PARAMETER_BOOL
            return new ROS2Parameter(name, value.getBoolValue());
         case 2: // PARAMETER_INTEGER
            return new ROS2Parameter(name, value.getIntegerValue());
         case 3: // PARAMETER_DOUBLE
            return new ROS2Parameter(name, value.getDoubleValue());
         case 4: // PARAMETER_STRING
            return new ROS2Parameter(name, value.getStringValueAsString());
         case 5: // PARAMETER_BYTE_ARRAY
            byte[] bytes = new byte[value.getByteArrayValue().size()];
            for (int i = 0; i < bytes.length; i++)
            {
               bytes[i] = value.getByteArrayValue().get(i);
            }
            return new ROS2Parameter(name, bytes);
         case 6: // PARAMETER_BOOL_ARRAY
            boolean[] bools = new boolean[value.getBoolArrayValue().size()];
            for (int i = 0; i < bools.length; i++)
            {
               bools[i] = value.getBoolArrayValue().get(i);
            }
            return new ROS2Parameter(name, bools);
         case 7: // PARAMETER_INTEGER_ARRAY
            long[] longs = new long[value.getIntegerArrayValue().size()];
            for (int i = 0; i < longs.length; i++)
            {
               longs[i] = value.getIntegerArrayValue().get(i);
            }
            return new ROS2Parameter(name, longs);
         case 8: // PARAMETER_DOUBLE_ARRAY
            double[] doubles = new double[value.getDoubleArrayValue().size()];
            for (int i = 0; i < doubles.length; i++)
            {
               doubles[i] = value.getDoubleArrayValue().get(i);
            }
            return new ROS2Parameter(name, doubles);
         case 9: // PARAMETER_STRING_ARRAY
            String[] strings = new String[value.getStringArrayValue().size()];
            for (int i = 0; i < strings.length; i++)
            {
               strings[i] = value.getStringArrayValue().get(i).toString();
            }
            return new ROS2Parameter(name, strings);
         case 0: // PARAMETER_NOT_SET
         default:
            return new ROS2Parameter(name);
      }
   }

   /**
    * Get the ROS 2 parameter type code for a given ROS2Parameter.ParameterType.
    */
   private byte getParameterTypeCode(ROS2Parameter.ParameterType type)
   {
      switch (type)
      {
         case PARAMETER_NOT_SET:
            return 0;
         case PARAMETER_BOOL:
            return 1;
         case PARAMETER_INTEGER:
            return 2;
         case PARAMETER_DOUBLE:
            return 3;
         case PARAMETER_STRING:
            return 4;
         case PARAMETER_BYTE_ARRAY:
            return 5;
         case PARAMETER_BOOL_ARRAY:
            return 6;
         case PARAMETER_INTEGER_ARRAY:
            return 7;
         case PARAMETER_DOUBLE_ARRAY:
            return 8;
         case PARAMETER_STRING_ARRAY:
            return 9;
         default:
            return 0;
      }
   }

   /**
    * Close all parameter service servers.
    */
   void close(Pointer fastddsParticipant)
   {
      if (getParametersServer != null)
         getParametersServer.close(fastddsParticipant);
      if (setParametersServer != null)
         setParametersServer.close(fastddsParticipant);
      if (setParametersAtomicallyServer != null)
         setParametersAtomicallyServer.close(fastddsParticipant);
      if (listParametersServer != null)
         listParametersServer.close(fastddsParticipant);
      if (describeParametersServer != null)
         describeParametersServer.close(fastddsParticipant);
      if (getParameterTypesServer != null)
         getParameterTypesServer.close(fastddsParticipant);
   }

   @Override
   public void close()
   {
      throw new UnsupportedOperationException("Use close(Pointer) instead");
   }
}
