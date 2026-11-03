package us.ihmc;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.ROS2Message;

/**
 * This is a custom handwritten ROS2Message meant to adapt to {@link MyPoint3D} objects.
 */
public class MyPoint3DMessage implements ROS2Message<MyPoint3DMessage>
{
   /*
    * Please note this static name field is REQUIRED!
    */
   public static final java.lang.String name = "my_interfaces::msg::dds_::MyPoint3D_";

   /*
    * Optional: the RIHS01 type hash (REP-2011) of my_interfaces/msg/MyPoint3D. jros2 advertises it to ROS 2 (Iron and newer)
    * in the endpoint USER_DATA ("typehash=...;"); without the field nothing is advertised. Generated classes contain it.
    * For a hand-written class copy it from the "type_hashes" entry of share/my_interfaces/msg/MyPoint3D.json, which rosidl
    * (Iron and newer) generates when the package is built with colcon.
    */
   public static final java.lang.String typeHash = "RIHS01_21f43f9a5ab8203e463ad0ea994081530f225702d0c70f36706abc50abf53be5";

   private final MyPoint3D src;

   /*
    * Please note that an argument-less constructor is REQUIRED!
    */
   public MyPoint3DMessage()
   {
      src = new MyPoint3D();
   }

   public MyPoint3DMessage(MyPoint3D src)
   {
      this.src = src;
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 8 + CDRBuffer.alignment(currentAlignment, 8); // src.x
      currentAlignment += 8 + CDRBuffer.alignment(currentAlignment, 8); // src.y
      currentAlignment += 8 + CDRBuffer.alignment(currentAlignment, 8); // src.z

      return currentAlignment - initialAlignment;
   }

   @Override
   public void serialize(CDRBuffer buffer)
   {
      buffer.writeDouble(src.getX());
      buffer.writeDouble(src.getY());
      buffer.writeDouble(src.getZ());
   }

   @Override
   public void deserialize(CDRBuffer buffer)
   {
      src.setX(buffer.readDouble());
      src.setY(buffer.readDouble());
      src.setZ(buffer.readDouble());
   }

   @Override
   public void set(MyPoint3DMessage from)
   {
      src.set(from.src);
   }

   public void set(MyPoint3D from)
   {
      src.set(from);
   }

   public MyPoint3D getSrc()
   {
      return src;
   }
}
