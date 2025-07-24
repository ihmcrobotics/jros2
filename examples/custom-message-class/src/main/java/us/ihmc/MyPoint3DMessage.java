package us.ihmc;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.jros2.ROS2Message;

public class MyPoint3DMessage implements ROS2Message<MyPoint3DMessage>
{
   public static final java.lang.String name = "my_interfaces::msg::dds_::MyPoint3D_";

   private final MyPoint3D src;

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
