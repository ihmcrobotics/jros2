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

import us.ihmc.fastddsjava.cdr.CDRSerializable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public interface ROS2Message<T extends ROS2Message<T>> extends CDRSerializable
{
   /**
    * Implemented by generated messages that have a {@code std_msgs/Header header} field (the ROS convention for stamped
    * messages). Publisher statistics read the age of a message through this interface, a plain call that allocates
    * nothing on any VM; for other message classes they fall back to {@link #getHeaderMethod(Class) reflection}.
    */
   interface WithHeader
   {
      /**
       * @return the {@code header} field of the message (not a copy)
       */
      std_msgs.Header getHeader();
   }

   void set(T from);

   static <T extends ROS2Message<T>> String getNameFromMessageClass(Class<T> topicType)
   {
      try
      {
         Field field = topicType.getField("name");

         field.setAccessible(true);

         return (String) field.get(null);
      }
      catch (NoSuchFieldException | IllegalAccessException e)
      {
         jros2.logError(e);
      }

      return null;
   }

   /**
    * Returns the RIHS01 type description hash (REP-2011) of a generated message class: its
    * {@code public static final String typeHash} field, {@code "RIHS01_"} followed by 64 hexadecimal digits (e.g.
    * {@code "RIHS01_df66..."}). rmw puts it in the endpoint USER_DATA as {@code typehash=<hash>;}.
    *
    * @return the hash, or null for classes generated before jros2 emitted type hashes, classes whose hash could not be
    *       computed (a referenced type was not available to the generator), and values that are not a well-formed RIHS01
    *       hash (rmw would reject them)
    */
   static String getTypeHashFromMessageClass(Class<?> topicType)
   {
      Object value;
      try
      {
         Field field = topicType.getField("typeHash");
         value = field.get(null);
      }
      catch (NoSuchFieldException | IllegalAccessException | NullPointerException e)
      {
         return null;
      }

      if (!(value instanceof String))
      {
         return null;
      }

      String hash = (String) value;
      if (hash.length() != 71 || !hash.startsWith("RIHS01_"))
      {
         return null;
      }

      for (int i = 7; i < hash.length(); ++i)
      {
         char c = hash.charAt(i);
         if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')))
         {
            return null;
         }
      }

      return hash;
   }

   static <T extends ROS2Message<T>> T createInstance(Class<T> topicType)
   {
      try
      {
         return topicType.getDeclaredConstructor().newInstance();
      }
      catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e)
      {
         jros2.logError(e);
      }

      return null;
   }

   /**
    * Finds the first method in a ROS2Message which returns type {@link std_msgs.Header}. Used for statistics.
    * @param topicType the ROS2Message topic type class.
    * @return the method reference to the Header getter.
    */
   static <T extends ROS2Message<T>> Method getHeaderMethod(Class<T> topicType)
   {
      Method[] methods = topicType.getDeclaredMethods();
      for (int i = 0; i < methods.length; ++i)
      {
         if (std_msgs.Header.class.equals(methods[i].getReturnType()))
         {
            return methods[i];
         }
      }

      return null;
   }
}
