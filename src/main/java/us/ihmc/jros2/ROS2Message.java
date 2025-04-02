package us.ihmc.jros2;

import us.ihmc.fastddsjava.cdr.CDRSerializable;

import java.lang.reflect.Field;

public interface ROS2Message<T extends ROS2Message<T>> extends CDRSerializable
{
   String getName();

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
         e.printStackTrace();
      }

      return null;
   }
}
