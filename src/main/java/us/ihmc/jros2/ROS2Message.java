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

import std_msgs.msg.dds.Header;
import us.ihmc.fastddsjava.cdr.CDRSerializable;
import us.ihmc.log.LogTools;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public interface ROS2Message<T extends ROS2Message<T>> extends CDRSerializable
{
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
         LogTools.error(e);
      }

      return null;
   }

   static <T extends ROS2Message<T>> T createInstance(Class<T> topicType)
   {
      try
      {
         return topicType.getDeclaredConstructor().newInstance();
      }
      catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e)
      {
         LogTools.error(e);
      }

      return null;
   }

   /**
    * Finds the first method in a ROS2Message which returns type {@link Header}. Used for statistics.
    * @param topicType the ROS2Message topic type class.
    * @return the method reference to the Header getter.
    */
   static <T extends ROS2Message<T>> Method getHeaderMethod(Class<T> topicType)
   {
      Method[] methods = topicType.getDeclaredMethods();
      for (int i = 0; i < methods.length; ++i)
      {
         if (Header.class.equals(methods[i].getReturnType()))
         {
            return methods[i];
         }
      }

      return null;
   }
}
