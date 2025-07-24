package us.ihmc;

import java.util.Objects;

public class MyPoint3D
{
   private double x;
   private double y;
   private double z;

   public MyPoint3D()
   {
      x = Double.NEGATIVE_INFINITY;
      y = Double.NEGATIVE_INFINITY;
      z = Double.NEGATIVE_INFINITY;
   }

   public double getX()
   {
      return x;
   }

   public void setX(double x)
   {
      this.x = x;
   }

   public double getY()
   {
      return y;
   }

   public void setY(double y)
   {
      this.y = y;
   }

   public double getZ()
   {
      return z;
   }

   public void setZ(double z)
   {
      this.z = z;
   }

   public MyPoint3D set(MyPoint3D other)
   {
      this.x = other.x;
      this.y = other.y;
      this.z = other.z;

      return this;
   }

   @Override
   public boolean equals(Object o)
   {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;
      MyPoint3D myPoint3D = (MyPoint3D) o;
      return Double.compare(myPoint3D.x, x) == 0 && Double.compare(myPoint3D.y, y) == 0 && Double.compare(myPoint3D.z, z) == 0;
   }

   @Override
   public int hashCode()
   {
      return Objects.hash(x, y, z);
   }
}
