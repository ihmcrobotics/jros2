/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Parameters of a {@link ROS2Node}, with the same rules and parameter services as rclcpp:
 * {@code <node>/list_parameters}, {@code get_parameters}, {@code get_parameter_types}, {@code set_parameters},
 * {@code set_parameters_atomically} and {@code describe_parameters}, plus change events on {@code /parameter_events}.
 * <p>
 * The services and the events publisher are created when the first parameter is declared. Undeclared parameters cannot be
 * set (rclcpp's default, {@code allow_undeclared_parameters = false}). Thread-safe.
 */
class ROS2NodeParameters
{
   static final String PARAMETER_EVENTS_TOPIC = "/parameter_events";
   static final String SEPARATOR = ".";

   /**
    * rmw_qos_profile_parameter_events: keep last 1000, reliable, volatile.
    */
   private static final ROS2QoSProfile PARAMETER_EVENTS_QOS = new ROS2QoSProfile();

   static
   {
      PARAMETER_EVENTS_QOS.history(ROS2QoSProfile.History.KEEP_LAST);
      PARAMETER_EVENTS_QOS.depth(1000);
      PARAMETER_EVENTS_QOS.reliability(ROS2QoSProfile.Reliability.RELIABLE);
      PARAMETER_EVENTS_QOS.durability(ROS2QoSProfile.Durability.VOLATILE);
   }

   private final ROS2Node node;
   private final String fullyQualifiedNodeName;
   private final Map<String, Entry> parameters = new LinkedHashMap<>();
   private final List<ROS2ParameterCallback> callbacks = new CopyOnWriteArrayList<>();
   private ROS2Publisher<ParameterEvent> eventPublisher;
   private boolean servicesStarted;

   private record Entry(ROS2Parameter parameter, ROS2ParameterDescriptor descriptor)
   {
   }

   ROS2NodeParameters(ROS2Node node)
   {
      this.node = node;
      fullyQualifiedNodeName = node.getName().startsWith("/") ? node.getName() : "/" + node.getName();
   }

   /*
    * Local API
    */

   synchronized ROS2Parameter declare(String name, Object defaultValue, ROS2ParameterDescriptor descriptor)
   {
      checkName(name);
      if (parameters.containsKey(name))
      {
         throw new IllegalStateException("parameter '" + name + "' has already been declared");
      }

      ROS2Parameter parameter = ROS2Parameter.of(name, defaultValue);
      String reason = runCallbacks(List.of(parameter));
      if (reason != null)
      {
         throw new IllegalArgumentException("parameter '" + name + "' could not be set: " + reason);
      }

      startServices();
      parameters.put(name, new Entry(parameter, descriptor == null ? ROS2ParameterDescriptor.DEFAULT : descriptor));
      publishEvent(List.of(parameter), List.of(), List.of());
      return parameter;
   }

   synchronized boolean has(String name)
   {
      return parameters.containsKey(name);
   }

   synchronized ROS2Parameter get(String name)
   {
      Entry entry = parameters.get(name);
      if (entry == null)
      {
         throw new IllegalStateException("parameter '" + name + "' is not declared");
      }
      return entry.parameter();
   }

   synchronized List<ROS2Parameter> getAll()
   {
      List<ROS2Parameter> all = new ArrayList<>();
      for (Entry entry : parameters.values())
         all.add(entry.parameter());
      return all;
   }

   synchronized ROS2ParameterDescriptor describe(String name)
   {
      Entry entry = parameters.get(name);
      if (entry == null)
      {
         throw new IllegalStateException("parameter '" + name + "' is not declared");
      }
      return entry.descriptor();
   }

   synchronized ROS2SetParametersResult set(List<ROS2Parameter> newParameters)
   {
      if (newParameters.isEmpty())
      {
         return ROS2SetParametersResult.failure("parameter list cannot be empty, this might be due to set_parameters_atomically being called with an empty list.");
      }

      // Validate all first, so the change is atomic
      for (ROS2Parameter parameter : newParameters)
      {
         Entry entry = parameters.get(parameter.getName());
         if (entry == null)
         {
            return ROS2SetParametersResult.failure("parameter '" + parameter.getName() + "' cannot be set because it was not declared");
         }
         if (entry.descriptor().readOnly())
         {
            return ROS2SetParametersResult.failure("parameter '" + parameter.getName() + "' cannot be set because it is read-only");
         }
         if (parameter.getType() == ROS2ParameterType.NOT_SET)
         {
            if (!entry.descriptor().dynamicTyping())
            {
               return ROS2SetParametersResult.failure("cannot undeclare a statically typed parameter");
            }
         }
         else if (!entry.descriptor().dynamicTyping() && parameter.getType() != entry.parameter().getType())
         {
            return ROS2SetParametersResult.failure("Wrong parameter type, parameter {" + parameter.getName() + "} is of type {"
                                                   + entry.parameter().getType().getRosName() + "}, setting it to {" + parameter.getType().getRosName()
                                                   + "} is not allowed.");
         }
      }

      String reason = runCallbacks(newParameters);
      if (reason != null)
      {
         return ROS2SetParametersResult.failure(reason);
      }

      List<ROS2Parameter> changed = new ArrayList<>();
      List<ROS2Parameter> deleted = new ArrayList<>();
      for (ROS2Parameter parameter : newParameters)
      {
         Entry entry = parameters.get(parameter.getName());
         if (parameter.getType() == ROS2ParameterType.NOT_SET)
         {
            parameters.remove(parameter.getName());
            deleted.add(entry.parameter());
         }
         else
         {
            parameters.put(parameter.getName(), new Entry(parameter, entry.descriptor()));
            changed.add(parameter);
         }
      }
      publishEvent(List.of(), changed, deleted);
      return ROS2SetParametersResult.SUCCESS;
   }

   synchronized void undeclare(String name)
   {
      Entry entry = parameters.get(name);
      if (entry == null)
      {
         throw new IllegalStateException("parameter '" + name + "' is not declared");
      }
      if (entry.descriptor().readOnly())
      {
         throw new IllegalStateException("cannot undeclare parameter '" + name + "' because it is read-only");
      }
      if (!entry.descriptor().dynamicTyping())
      {
         throw new IllegalStateException("cannot undeclare a statically typed parameter");
      }
      parameters.remove(name);
      publishEvent(List.of(), List.of(), List.of(entry.parameter()));
   }

   void addCallback(ROS2ParameterCallback callback)
   {
      callbacks.add(callback);
   }

   boolean removeCallback(ROS2ParameterCallback callback)
   {
      return callbacks.remove(callback);
   }

   /**
    * rclcpp's list_parameters: names matching any prefix (or all if none) up to the given depth of '.' separators, plus the
    * prefixes of the returned names.
    */
   synchronized List<List<String>> list(List<String> prefixes, long depth)
   {
      List<String> names = new ArrayList<>();
      List<String> resultPrefixes = new ArrayList<>();
      boolean recursive = depth == ListParameters_Request.DEPTH_RECURSIVE;

      for (String name : parameters.keySet())
      {
         boolean matches;
         if (prefixes.isEmpty())
         {
            // DEPTH_RECURSIVE (0) without prefixes lists everything
            matches = recursive || countSeparators(name) < depth;
         }
         else
         {
            matches = false;
            for (String prefix : prefixes)
            {
               if (name.equals(prefix))
               {
                  matches = true;
               }
               else if (name.startsWith(prefix + SEPARATOR))
               {
                  matches = recursive || countSeparators(name.substring(prefix.length() + 1)) < depth;
               }
               if (matches)
                  break;
            }
         }

         if (matches)
         {
            names.add(name);
            int lastSeparator = name.lastIndexOf(SEPARATOR);
            if (lastSeparator != -1)
            {
               String prefix = name.substring(0, lastSeparator);
               if (!resultPrefixes.contains(prefix))
                  resultPrefixes.add(prefix);
            }
         }
      }

      return List.of(names, resultPrefixes);
   }

   private static int countSeparators(String name)
   {
      int count = 0;
      for (int i = 0; i < name.length(); i++)
      {
         if (name.charAt(i) == '.')
            count++;
      }
      return count;
   }

   private static void checkName(String name)
   {
      if (name == null || name.isEmpty())
      {
         throw new IllegalArgumentException("parameter name must not be empty");
      }
   }

   private String runCallbacks(List<ROS2Parameter> parameters)
   {
      for (ROS2ParameterCallback callback : callbacks)
      {
         String reason;
         try
         {
            reason = callback.onSetParameters(Collections.unmodifiableList(parameters));
         }
         catch (RuntimeException e)
         {
            reason = "parameter callback threw " + e;
         }
         if (reason != null)
            return reason;
      }
      return null;
   }

   /*
    * Services and events
    */

   private void startServices()
   {
      if (servicesStarted)
         return;
      servicesStarted = true;

      eventPublisher = node.createPublisher(new ROS2Topic<>(PARAMETER_EVENTS_TOPIC, ParameterEvent.class, PARAMETER_EVENTS_QOS));

      String prefix = fullyQualifiedNodeName + "/";
      node.createService(prefix + "list_parameters", ListParameters_Request.class, ListParameters_Response.class, (request, response) ->
      {
         List<String> requestedPrefixes = new ArrayList<>();
         for (int i = 0; i < request.getPrefixes().size(); i++)
            requestedPrefixes.add(request.getPrefixes().getAsString(i));
         List<List<String>> result = list(requestedPrefixes, request.getDepth());
         response.getResult().getNames().addAll(result.get(0));
         response.getResult().getPrefixes().addAll(result.get(1));
      });
      node.createService(prefix + "get_parameters", GetParameters_Request.class, GetParameters_Response.class, (request, response) ->
      {
         synchronized (this)
         {
            for (int i = 0; i < request.getNames().size(); i++)
            {
               Entry entry = parameters.get(request.getNames().getAsString(i));
               ParameterValue value = response.getValues().add();
               if (entry != null)
                  entry.parameter().toMessage(value);
            }
         }
      });
      node.createService(prefix + "get_parameter_types", GetParameterTypes_Request.class, GetParameterTypes_Response.class, (request, response) ->
      {
         synchronized (this)
         {
            for (int i = 0; i < request.getNames().size(); i++)
            {
               Entry entry = parameters.get(request.getNames().getAsString(i));
               response.getTypes().add(entry == null ? ROS2ParameterType.NOT_SET.getId() : entry.parameter().getType().getId());
            }
         }
      });
      node.createService(prefix + "set_parameters", SetParameters_Request.class, SetParameters_Response.class, (request, response) ->
      {
         // Each parameter is set on its own
         for (Parameter parameter : request.getParameters())
         {
            ROS2SetParametersResult result = set(List.of(ROS2Parameter.fromMessage(parameter.getNameAsString(), parameter.getValue())));
            SetParametersResult resultMessage = response.getResults().add();
            resultMessage.setSuccessful(result.successful());
            resultMessage.setReason(result.reason());
         }
      });
      node.createService(prefix + "set_parameters_atomically",
                         SetParametersAtomically_Request.class,
                         SetParametersAtomically_Response.class,
                         (request, response) ->
                         {
                            List<ROS2Parameter> newParameters = new ArrayList<>();
                            for (Parameter parameter : request.getParameters())
                               newParameters.add(ROS2Parameter.fromMessage(parameter.getNameAsString(), parameter.getValue()));
                            ROS2SetParametersResult result = set(newParameters);
                            response.getResult().setSuccessful(result.successful());
                            response.getResult().setReason(result.reason());
                         });
      node.createService(prefix + "describe_parameters", DescribeParameters_Request.class, DescribeParameters_Response.class, (request, response) ->
      {
         synchronized (this)
         {
            for (int i = 0; i < request.getNames().size(); i++)
            {
               String name = request.getNames().getAsString(i);
               Entry entry = parameters.get(name);
               ParameterDescriptor descriptor = response.getDescriptors().add();
               descriptor.setName(name);
               if (entry != null)
               {
                  descriptor.setType(entry.parameter().getType().getId());
                  descriptor.setDescription(entry.descriptor().description());
                  descriptor.setReadOnly(entry.descriptor().readOnly());
                  descriptor.setDynamicTyping(entry.descriptor().dynamicTyping());
               }
            }
         }
      });
   }

   private void publishEvent(List<ROS2Parameter> added, List<ROS2Parameter> changed, List<ROS2Parameter> deleted)
   {
      if (eventPublisher == null)
         return;

      ParameterEvent event = new ParameterEvent();
      long nowNanos = System.currentTimeMillis() * 1_000_000L;
      event.getStamp().setSec((int) (nowNanos / 1_000_000_000L));
      event.getStamp().setNanosec((int) (nowNanos % 1_000_000_000L));
      event.setNode(fullyQualifiedNodeName);
      for (ROS2Parameter parameter : added)
         parameter.toMessage(event.getNewParameters().add());
      for (ROS2Parameter parameter : changed)
         parameter.toMessage(event.getChangedParameters().add());
      for (ROS2Parameter parameter : deleted)
         event.getDeletedParameters().add().setName(parameter.getName());
      eventPublisher.publish(event);
   }
}
