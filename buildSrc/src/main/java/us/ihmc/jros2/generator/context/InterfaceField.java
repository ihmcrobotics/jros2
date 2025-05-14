package us.ihmc.jros2.generator.context;

import java.util.Locale;

public class InterfaceField
{
   private String type;
   private String javaType;
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

   public String getJavaType()
   {
      if (javaType != null)
      {
         return javaType;
      }
      else
      {
         return inferJavaType();
      }
   }

   public void javaType(String javaType)
   {
      this.javaType = javaType;
   }

   public String getName()
   {
      return name;
   }

   public void name(String name)
   {
      this.name = name;
   }

   public String getJavaName()
   {
      if (name != null)
      {
         if (constantValue != null)
         {
            // upper snake case constants
            return name.toUpperCase(Locale.getDefault());
         }
         else
         {
            // postfix with _ to avoid conflict with Java reserved words (e.g. "default")
            return name + "_";
         }
      }

      return null;
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

   // TODO: Change name
   public boolean getArray()
   {
      return array;
   }

   public void array(boolean array)
   {
      this.array = array;
   }

   // TODO: Change name
   public boolean getUpperBounded()
   {
      return upperBounded;
   }

   public void upperBounded(boolean upperBounded)
   {
      this.upperBounded = upperBounded;
   }

   // TODO: Change name
   public boolean getUnbounded()
   {
      return unbounded;
   }

   public void unbounded(boolean unbounded)
   {
      this.unbounded = unbounded;
   }

   // TODO: Change name
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
      if (headerComment != null && headerComment.isEmpty())
      {
         headerComment = null;
      }

      this.headerComment = headerComment;
   }

   public String getTrailingComment()
   {
      return trailingComment;
   }

   public void trailingComment(String trailingComment)
   {
      if (trailingComment != null && trailingComment.isEmpty())
      {
         trailingComment = null;
      }

      this.trailingComment = trailingComment;
   }

   public boolean isBuiltinType()
   {
      return Builtin.isBuiltinType(type);
   }

   public boolean isBuiltinStringType()
   {
      return type != null && (type.equals("string") || type.equals("wstring"));
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

   private String inferJavaType()
   {
      if (type == null)
      {
         throw new RuntimeException("Cannot infer Java type, InterfaceField type has not been set");
      }

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
}
