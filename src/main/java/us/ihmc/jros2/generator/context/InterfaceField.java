package us.ihmc.jros2.generator.context;

public class InterfaceField // Cannot be a record
{
   private String type;
   private String name;
   private boolean stringUpperBounded;
   private int stringLength;
   private boolean array;
   private boolean upperBounded;
   private boolean unbounded;
   private int length;
   private String constantValue;
   private String defaultValue;
   private String headerComment;
   private String trailingComment;

   public String getType()
   {
      return type;
   }

   public void type(String type)
   {
      this.type = type;
   }

   public String getName()
   {
      return name;
   }

   public void name(String name)
   {
      this.name = name;
   }

   public boolean isStringUpperBounded()
   {
      return stringUpperBounded;
   }

   public void stringUpperBounded(boolean stringUpperBounded)
   {
      this.stringUpperBounded = stringUpperBounded;
   }

   public int getStringLength()
   {
      return stringLength;
   }

   public void stringLength(int stringLength)
   {
      this.stringLength = stringLength;
   }

   public boolean isArray()
   {
      return array;
   }

   public void array(boolean array)
   {
      this.array = array;
   }

   public boolean isUpperBounded()
   {
      return upperBounded;
   }

   public void upperBounded(boolean upperBounded)
   {
      this.upperBounded = upperBounded;
   }

   public boolean isUnbounded()
   {
      return unbounded;
   }

   public void unbounded(boolean unbounded)
   {
      this.unbounded = unbounded;
   }

   public int getLength()
   {
      return length;
   }

   public void length(int length)
   {
      this.length = length;
   }

   public String getConstantValue()
   {
      return constantValue;
   }

   public void constantValue(String constantValue)
   {
      this.constantValue = constantValue;
   }

   public String getDefaultValue()
   {
      return defaultValue;
   }

   public void defaultValue(String defaultValue)
   {
      this.defaultValue = defaultValue;
   }

   public String getHeaderComment()
   {
      return headerComment;
   }

   public void headerComment(String headerComment)
   {
      this.headerComment = headerComment;
   }

   public String getTrailingComment()
   {
      return trailingComment;
   }

   public void trailingComment(String trailingComment)
   {
      this.trailingComment = trailingComment;
   }
}
