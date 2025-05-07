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

   public boolean isBuiltinType()
   {
      if (type != null)
      {
         switch (type)
         {
            case "bool":
            case "byte":
            case "char":
            case "float32":
            case "float64":
            case "int8":
            case "uint8":
            case "int16":
            case "uint16":
            case "int32":
            case "uint32":
            case "int64":
            case "uint64":
            case "string":
            case "wstring":
               return true;
         }
      }

      return false;
   }

   public int getBuiltinTypeSize()
   {
      if (type != null)
      {
         switch (type)
         {
            case "bool":
            case "byte":
            case "char":
            case "uint8":
            case "int8":
               return 1;
            case "int16":
            case "uint16":
               return 2;
            case "float32":
            case "uint32":
            case "int32":
               return 4;
            case "float64":
            case "uint64":
            case "int64":
               return 8;
            case "string":
            case "wstring":
               return -1; // TODO:
         }
      }

      return -1;
   }

   public String getBuiltinTypeJavaType()
   {
      if (type != null)
      {
         switch (type)
         {
            case "bool":
               return "boolean";
            case "byte":
            case "uint8":
            case "int8":
               return "byte";
            case "char":
               return "char";
            case "int16":
            case "uint16":
               return "short";
            case "float32":
               return "float";
            case "uint32":
            case "int32":
               return "int";
            case "float64":
               return "double";
            case "uint64":
            case "int64":
               return "long";
            case "string":
            case "wstring":
               return null; // TODO:
         }
      }

      return null;
   }

   public String getBuiltinTypeIDLSequenceType()
   {
      if (type != null)
      {
         switch (type)
         {
            case "bool":
               return "IDLBoolSequence";
            case "byte":
            case "uint8":
            case "int8":
               return "IDLByteSequence";
            case "char":
               return "IDLCharSequence";
            case "int16":
            case "uint16":
               return "IDLShortSequence";
            case "float32":
               return "IDLFloatSequence";
            case "uint32":
            case "int32":
               return "IDLIntSequence";
            case "float64":
               return "IDLDoubleSequence";
            case "uint64":
            case "int64":
               return "IDLLongSequence";
            case "string":
            case "wstring":
               return null; // TODO:
         }
      }

      return null;
   }

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

   public String getBuiltinCDRBufferWriteMethod()
   {
      if (isBuiltinType())
      {
         switch (type)
         {
            case "bool":
               return "writeBoolean";
            case "byte":
            case "uint8":
            case "int8":
               return "writeByte";
            case "char":
               return "writeChar";
            case "int16":
            case "uint16":
               return "writeShort";
            case "float32":
               return "writeFloat";
            case "uint32":
            case "int32":
               return "writeInt";
            case "float64":
               return "writeDouble";
            case "uint64":
            case "int64":
               return "writeLong";
            case "string":
            case "wstring":
               return ""; // TODO:
         }
      }

      return null;
   }

   public String getBuiltinCDRBufferReadMethod()
   {
      if (isBuiltinType())
      {
         switch (type)
         {
            case "bool":
               return "readBoolean";
            case "byte":
            case "uint8":
            case "int8":
               return "readByte";
            case "char":
               return "readChar";
            case "int16":
            case "uint16":
               return "readShort";
            case "float32":
               return "readFloat";
            case "uint32":
            case "int32":
               return "readInt";
            case "float64":
               return "readDouble";
            case "uint64":
            case "int64":
               return "readLong";
            case "string":
            case "wstring":
               return ""; // TODO:
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
