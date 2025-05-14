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
