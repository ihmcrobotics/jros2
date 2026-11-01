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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Finds the messages generated for an action from its goal, result and feedback classes, and reads and writes their members.
 * For {@code example_interfaces.Fibonacci_Goal} these are {@code Fibonacci_SendGoal_Request}, {@code Fibonacci_SendGoal_Response},
 * {@code Fibonacci_GetResult_Request}, {@code Fibonacci_GetResult_Response} and {@code Fibonacci_FeedbackMessage} in the same
 * package. They are only known at runtime, so they are handled through raw types and reflection; actions are low rate.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class ROS2ActionTypeSupport<G extends ROS2Message<G>, R extends ROS2Message<R>, F extends ROS2Message<F>>
{
   final Class<G> goalType;
   final Class<R> resultType;
   final Class<F> feedbackType;
   final String actionTypeName;

   final Class sendGoalRequestType;
   final Class sendGoalResponseType;
   final Class getResultRequestType;
   final Class getResultResponseType;
   final Class feedbackMessageType;

   private final Method sendGoalRequestGoalId;
   private final Method sendGoalRequestGoal;
   private final Method sendGoalResponseGetAccepted;
   private final Method sendGoalResponseSetAccepted;
   private final Method sendGoalResponseStamp;
   private final Method getResultRequestGoalId;
   private final Method getResultResponseGetStatus;
   private final Method getResultResponseSetStatus;
   private final Method getResultResponseResult;
   private final Method feedbackMessageGoalId;
   private final Method feedbackMessageFeedback;

   ROS2ActionTypeSupport(Class<G> goalType, Class<R> resultType, Class<F> feedbackType)
   {
      this.goalType = goalType;
      this.resultType = resultType;
      this.feedbackType = feedbackType;

      String goalName = goalType.getName();
      if (!goalName.endsWith("_Goal"))
      {
         throw new IllegalArgumentException("Not a generated action goal class (expected <Action>_Goal): " + goalName);
      }
      String base = goalName.substring(0, goalName.length() - "_Goal".length());
      if (!resultType.getName().equals(base + "_Result") || !feedbackType.getName().equals(base + "_Feedback"))
      {
         throw new IllegalArgumentException("Result and feedback types must be " + base + "_Result and " + base + "_Feedback");
      }
      actionTypeName = base;

      try
      {
         ClassLoader classLoader = goalType.getClassLoader();
         sendGoalRequestType = Class.forName(base + "_SendGoal_Request", true, classLoader);
         sendGoalResponseType = Class.forName(base + "_SendGoal_Response", true, classLoader);
         getResultRequestType = Class.forName(base + "_GetResult_Request", true, classLoader);
         getResultResponseType = Class.forName(base + "_GetResult_Response", true, classLoader);
         feedbackMessageType = Class.forName(base + "_FeedbackMessage", true, classLoader);

         sendGoalRequestGoalId = sendGoalRequestType.getMethod("getGoalId");
         sendGoalRequestGoal = sendGoalRequestType.getMethod("getGoal");
         sendGoalResponseGetAccepted = sendGoalResponseType.getMethod("getAccepted");
         sendGoalResponseSetAccepted = sendGoalResponseType.getMethod("setAccepted", boolean.class);
         sendGoalResponseStamp = sendGoalResponseType.getMethod("getStamp");
         getResultRequestGoalId = getResultRequestType.getMethod("getGoalId");
         getResultResponseGetStatus = getResultResponseType.getMethod("getStatus");
         getResultResponseSetStatus = getResultResponseType.getMethod("setStatus", byte.class);
         getResultResponseResult = getResultResponseType.getMethod("getResult");
         feedbackMessageGoalId = feedbackMessageType.getMethod("getGoalId");
         feedbackMessageFeedback = feedbackMessageType.getMethod("getFeedback");
      }
      catch (ClassNotFoundException | NoSuchMethodException e)
      {
         throw new IllegalArgumentException("Generated messages of action " + base + " not found; generate the whole .action", e);
      }
   }

   /*
    * Messages
    */

   ROS2Message newSendGoalRequest()
   {
      return ROS2Message.createInstance(sendGoalRequestType);
   }

   ROS2Message newSendGoalResponse()
   {
      return ROS2Message.createInstance(sendGoalResponseType);
   }

   ROS2Message newGetResultRequest()
   {
      return ROS2Message.createInstance(getResultRequestType);
   }

   ROS2Message newGetResultResponse()
   {
      return ROS2Message.createInstance(getResultResponseType);
   }

   ROS2Message newFeedbackMessage()
   {
      return ROS2Message.createInstance(feedbackMessageType);
   }

   G newGoal()
   {
      return ROS2Message.createInstance(goalType);
   }

   R newResult()
   {
      return ROS2Message.createInstance(resultType);
   }

   F newFeedback()
   {
      return ROS2Message.createInstance(feedbackType);
   }

   /*
    * Members
    */

   unique_identifier_msgs.UUID sendGoalRequestGoalId(ROS2Message request)
   {
      return (unique_identifier_msgs.UUID) invoke(sendGoalRequestGoalId, request);
   }

   G sendGoalRequestGoal(ROS2Message request)
   {
      return (G) invoke(sendGoalRequestGoal, request);
   }

   boolean sendGoalResponseAccepted(ROS2Message response)
   {
      return (Boolean) invoke(sendGoalResponseGetAccepted, response);
   }

   void sendGoalResponseAccepted(ROS2Message response, boolean accepted)
   {
      invoke(sendGoalResponseSetAccepted, response, accepted);
   }

   builtin_interfaces.Time sendGoalResponseStamp(ROS2Message response)
   {
      return (builtin_interfaces.Time) invoke(sendGoalResponseStamp, response);
   }

   unique_identifier_msgs.UUID getResultRequestGoalId(ROS2Message request)
   {
      return (unique_identifier_msgs.UUID) invoke(getResultRequestGoalId, request);
   }

   byte getResultResponseStatus(ROS2Message response)
   {
      return (Byte) invoke(getResultResponseGetStatus, response);
   }

   void getResultResponseStatus(ROS2Message response, byte status)
   {
      invoke(getResultResponseSetStatus, response, status);
   }

   R getResultResponseResult(ROS2Message response)
   {
      return (R) invoke(getResultResponseResult, response);
   }

   unique_identifier_msgs.UUID feedbackMessageGoalId(ROS2Message message)
   {
      return (unique_identifier_msgs.UUID) invoke(feedbackMessageGoalId, message);
   }

   F feedbackMessageFeedback(ROS2Message message)
   {
      return (F) invoke(feedbackMessageFeedback, message);
   }

   private static Object invoke(Method method, Object target, Object... args)
   {
      try
      {
         return method.invoke(target, args);
      }
      catch (IllegalAccessException | InvocationTargetException e)
      {
         throw new IllegalStateException("Failed to call " + method, e);
      }
   }

   /*
    * Goal ids
    */

   static UUID toUUID(unique_identifier_msgs.UUID message)
   {
      ByteBuffer buffer = ByteBuffer.wrap(message.getUuid());
      return new UUID(buffer.getLong(), buffer.getLong());
   }

   static void toMessage(UUID uuid, unique_identifier_msgs.UUID message)
   {
      ByteBuffer.wrap(message.getUuid()).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
   }

   static long toNanos(builtin_interfaces.Time time)
   {
      return time.getSec() * 1_000_000_000L + Integer.toUnsignedLong(time.getNanosec());
   }

   static void toMessage(long nanos, builtin_interfaces.Time time)
   {
      time.setSec((int) (nanos / 1_000_000_000L));
      time.setNanosec((int) (nanos % 1_000_000_000L));
   }

   static long nowNanos()
   {
      java.time.Instant now = java.time.Instant.now();
      return now.getEpochSecond() * 1_000_000_000L + now.getNano();
   }
}
