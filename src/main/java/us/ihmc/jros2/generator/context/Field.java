package us.ihmc.jros2.generator.context;

public class Field // Cannot be a record
{
   private final String type;
   private final String name;
   private final boolean array;
   private final boolean upperBounded;
   private final boolean unbounded;
   private final int length;

   public Field(String type, String name, boolean array, boolean upperBounded, boolean unbounded, int length)
   {
      this.type = type;
      this.name = name;
      this.array = array;
      this.upperBounded = upperBounded;
      this.unbounded = unbounded;
      this.length = length;
   }

   public String getType()
   {
      return type;
   }

   public String getName()
   {
      return name;
   }

   public boolean isArray()
   {
      return array;
   }

   public boolean isUpperBounded()
   {
      return upperBounded;
   }

   public boolean isUnbounded()
   {
      return unbounded;
   }

   public int getLength()
   {
      return length;
   }
}
