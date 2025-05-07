package us.ihmc.jros2.generator.context;

/**
 * TODO: Allow the generator to take in a custom map of type name to InterfaceField
 *    This is so a user-developer can override generated ROS2Message types with their
 *    own implementation.
 */
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

   /**
    * Get the type name as defined in the ROS 2 .msg file
    *
    * @return the type name
    */
   public String getType()
   {
      return type;
   }

   public void type(String type)
   {
      this.type = type;
   }

   public boolean isBuiltinType()
   {
      return Builtin.isBuiltinType(type);
   }

   public int getBuiltinTypeSize()
   {
      return Builtin.getBuiltinTypeSize(type);
   }

   public String getBuiltinTypeJavaType()
   {
      return Builtin.getBuiltinTypeJavaType(type);
   }

   public String getBuiltinTypeIDLSequenceType()
   {
      return Builtin.getBuiltinTypeIDLSequenceType(type);
   }

   public String getBuiltinCDRBufferWriteMethod()
   {
      return Builtin.getBuiltinCDRBufferWriteMethod(type);
   }

   public String getBuiltinCDRBufferReadMethod()
   {
      return Builtin.getBuiltinCDRBufferReadMethod(type);
   }

   /**
    * The Java type to represent the ROS 2 interface field type
    * Handles arrays of fixed size, unbounded arrays, and arrays with an upper bound limit
    *
    * @return the class name of the Java type (no package prepended) or null if the type was not set
    */
   public String getJavaType()
   {
      if (type != null)
      {
         if (getArray() && !getFixedSize())
         {
            if (isBuiltinType())
            {
               return getBuiltinTypeIDLSequenceType();
            }
            else
            {
               return "IDLObjectSequence<" + getType() + ">";
            }
         }
         else
         {
            if (isBuiltinType())
            {
               if (getType().equals("string") || getType().equals("wstring"))
               {
                  return "StringBuilder";
               }
               else
               {
                  return getBuiltinTypeJavaType();
               }
            }
            else
            {
               return getType();
            }
         }
      }

      return null;
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

   public boolean getArray()
   {
      return array;
   }

   public void array(boolean array)
   {
      this.array = array;
   }

   public boolean getUpperBounded()
   {
      return upperBounded;
   }

   public void upperBounded(boolean upperBounded)
   {
      this.upperBounded = upperBounded;
   }

   public boolean getUnbounded()
   {
      return unbounded;
   }

   public void unbounded(boolean unbounded)
   {
      this.unbounded = unbounded;
   }

   public boolean getFixedSize()
   {
      return !getUpperBounded() && !getUnbounded();
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
