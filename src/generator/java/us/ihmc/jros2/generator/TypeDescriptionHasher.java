/*
 *  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package us.ihmc.jros2.generator;

import us.ihmc.jros2.parser.InterfaceContext;
import us.ihmc.jros2.parser.field.InterfaceField;
import us.ihmc.jros2.parser.util.BuiltinTools;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Computes RIHS01 type description hashes (REP-2011), exactly as {@code rosidl_generator_type_description} (ROS 2 Jazzy
 * and later) does for .msg files:
 * <pre>
 * hash = "RIHS01_" + lowercase_hex(SHA-256(UTF-8(canonical JSON of the TypeDescription without default values)))
 * </pre>
 * The TypeDescription is the type's own field list plus the field lists of all transitively referenced types, sorted by
 * type name. Constants, comments and default values are not part of the hash. A type without fields gets the member
 * {@code uint8 structure_needs_at_least_one_member}.
 * <p>
 * Referenced types are looked up with a resolver function from a ROS type name ({@code pkg/msg/Type}) to its parsed
 * definition.
 * </p>
 */
public final class TypeDescriptionHasher
{
   /** Name of the member ROS 2 adds to a structure without fields. */
   public static final String EMPTY_STRUCTURE_MEMBER_NAME = "structure_needs_at_least_one_member";

   static final int FIELD_TYPE_NESTED_TYPE = 1;
   static final int FIELD_TYPE_UINT8 = 3;
   static final int ARRAY_OFFSET = 48;
   static final int BOUNDED_SEQUENCE_OFFSET = 96;
   static final int UNBOUNDED_SEQUENCE_OFFSET = 144;

   /**
    * The type of one member in a type description ({@code type_description_interfaces/msg/Field} without the default
    * value).
    */
   public static final class FieldDescription
   {
      private final String name;
      private final int typeId;
      private final long capacity;
      private final long stringCapacity;
      private final String nestedTypeName;

      public FieldDescription(String name, int typeId, long capacity, long stringCapacity, String nestedTypeName)
      {
         this.name = name;
         this.typeId = typeId;
         this.capacity = capacity;
         this.stringCapacity = stringCapacity;
         this.nestedTypeName = nestedTypeName;
      }

      public String getName()
      {
         return name;
      }

      public int getTypeId()
      {
         return typeId;
      }

      public long getCapacity()
      {
         return capacity;
      }

      public long getStringCapacity()
      {
         return stringCapacity;
      }

      /**
       * @return {@code pkg/msg/Type} for nested types (and arrays of them), otherwise an empty string
       */
      public String getNestedTypeName()
      {
         return nestedTypeName;
      }
   }

   /**
    * Thrown when a referenced type cannot be resolved.
    */
   public static class UnresolvedTypeException extends RuntimeException
   {
      private static final long serialVersionUID = 1L;

      public UnresolvedTypeException(String message)
      {
         super(message);
      }
   }

   private final Function<String, ? extends InterfaceContext> resolver;
   private final Map<String, List<FieldDescription>> cache = new HashMap<>();

   /**
    * @param resolver returns the parsed definition of a ROS type name ({@code pkg/msg/Type}), or null if it is unknown
    */
   public TypeDescriptionHasher(Function<String, ? extends InterfaceContext> resolver)
   {
      this.resolver = resolver;
   }

   /**
    * @param context a parsed message definition
    * @return the RIHS01 hash string, e.g. {@code RIHS01_df668c74...}
    * @throws UnresolvedTypeException if a (transitively) referenced type cannot be resolved
    */
   public String hash(InterfaceContext context)
   {
      return hashOfJson(hashableJson(context));
   }

   /**
    * @param typeName a ROS type name, {@code pkg/msg/Type}
    * @return the RIHS01 hash string
    * @throws UnresolvedTypeException if the type or a referenced type cannot be resolved
    */
   public String hash(String typeName)
   {
      return hashOfJson(hashableJson(typeName));
   }

   /**
    * @return the canonical JSON that is hashed for {@code context}
    */
   public String hashableJson(InterfaceContext context)
   {
      return hashableJson(context.getRosTypeName(), describe(context));
   }

   /**
    * @return the canonical JSON that is hashed for {@code typeName}
    */
   public String hashableJson(String typeName)
   {
      return hashableJson(typeName, fieldsOf(typeName, null));
   }

   private String hashableJson(String typeName, List<FieldDescription> topLevelFields)
   {
      TreeSet<String> referencedTypes = new TreeSet<>(); // String natural order == Python sorted() for ASCII names
      Deque<String[]> queue = new ArrayDeque<>(); // {type name, referencing type name}
      for (FieldDescription field : topLevelFields)
      {
         if (!field.getNestedTypeName().isEmpty())
            queue.add(new String[] {field.getNestedTypeName(), typeName});
      }

      while (!queue.isEmpty())
      {
         String[] next = queue.pop();
         if (referencedTypes.add(next[0]))
         {
            for (FieldDescription field : fieldsOf(next[0], next[1]))
            {
               if (!field.getNestedTypeName().isEmpty())
                  queue.add(new String[] {field.getNestedTypeName(), next[0]});
            }
         }
      }

      StringBuilder json = new StringBuilder();
      json.append("{\"type_description\": ");
      appendIndividualTypeDescription(json, typeName, topLevelFields);
      json.append(", \"referenced_type_descriptions\": [");
      boolean first = true;
      for (String referencedType : referencedTypes)
      {
         if (!first)
            json.append(", ");
         first = false;
         appendIndividualTypeDescription(json, referencedType, fieldsOf(referencedType, typeName));
      }
      json.append("]}");
      return json.toString();
   }

   private List<FieldDescription> fieldsOf(String typeName, String referencedBy)
   {
      List<FieldDescription> fields = cache.get(typeName);
      if (fields == null)
      {
         InterfaceContext context = resolver.apply(typeName);
         if (context == null)
         {
            throw new UnresolvedTypeException("Cannot resolve the definition of " + typeName + (referencedBy == null ? "" : " (referenced by " + referencedBy + ")"));
         }
         fields = describe(context);
         cache.put(typeName, fields);
      }
      return fields;
   }

   /**
    * Describes the members of a message (constants excluded; the dummy member for a structure without fields).
    */
   public static List<FieldDescription> describe(InterfaceContext context)
   {
      List<FieldDescription> fields = new ArrayList<>();
      for (InterfaceField field : context.getFields().values())
      {
         if (field.getConstantValue() == null)
         {
            fields.add(describeField(context, field));
         }
      }

      if (fields.isEmpty())
      {
         fields.add(new FieldDescription(EMPTY_STRUCTURE_MEMBER_NAME, FIELD_TYPE_UINT8, 0, 0, ""));
      }

      return Collections.unmodifiableList(fields);
   }

   static FieldDescription describeField(InterfaceContext context, InterfaceField field)
   {
      String type = field.getType();
      boolean builtin = BuiltinTools.isBuiltinType(type);
      boolean bounded = field.isBuiltinStringType() && field.getStringLength() > 0;
      int typeId = builtin ? baseTypeId(type, bounded) : FIELD_TYPE_NESTED_TYPE;

      String nestedTypeName = "";
      if (!builtin)
      {
         int slash = type.lastIndexOf('/');
         String typePackage = slash >= 0 ? type.substring(0, slash) : context.getPackageName();
         nestedTypeName = typePackage + "/msg/" + type.substring(slash + 1);
      }

      long stringCapacity = bounded ? field.getStringLength() : 0;
      long capacity = 0;
      if (field.isArray())
      {
         if (field.isFixedSize())
         {
            typeId += ARRAY_OFFSET;
            capacity = field.getLength();
         }
         else if (field.isUpperBounded())
         {
            typeId += BOUNDED_SEQUENCE_OFFSET;
            capacity = field.getLength();
         }
         else if (type.equals("wstring") && bounded)
         {
            // rosidl_parser quirk (Jazzy, Kilted, Lyrical): sequence<wstring<N>> is parsed as
            // BoundedSequence(BoundedWString(N), N), so wstring<=N[] hashes as a bounded sequence of capacity N.
            typeId += BOUNDED_SEQUENCE_OFFSET;
            capacity = stringCapacity;
         }
         else
         {
            typeId += UNBOUNDED_SEQUENCE_OFFSET;
         }
      }

      return new FieldDescription(field.getName(), typeId, capacity, stringCapacity, nestedTypeName);
   }

   /**
    * {@code type_description_interfaces/msg/FieldType} ids after the .msg to IDL mapping of {@code rosidl_adapter}
    * ({@code char} is {@code uint8}, {@code byte} is {@code octet}).
    */
   static int baseTypeId(String builtinType, boolean boundedString)
   {
      return switch (builtinType)
      {
         case "int8" -> 2;
         case "uint8", "char" -> 3;
         case "int16" -> 4;
         case "uint16" -> 5;
         case "int32" -> 6;
         case "uint32" -> 7;
         case "int64" -> 8;
         case "uint64" -> 9;
         case "float32" -> 10;
         case "float64" -> 11;
         case "bool" -> 15;
         case "byte" -> 16;
         case "string" -> boundedString ? 21 : 17;
         case "wstring" -> boundedString ? 22 : 18;
         default -> throw new IllegalArgumentException("Not a built-in type: " + builtinType);
      };
   }

   private static void appendIndividualTypeDescription(StringBuilder json, String typeName, List<FieldDescription> fields)
   {
      json.append("{\"type_name\": ");
      appendJsonString(json, typeName);
      json.append(", \"fields\": [");
      for (int i = 0; i < fields.size(); i++)
      {
         FieldDescription field = fields.get(i);
         if (i > 0)
            json.append(", ");
         json.append("{\"name\": ");
         appendJsonString(json, field.getName());
         json.append(", \"type\": {\"type_id\": ").append(field.getTypeId());
         json.append(", \"capacity\": ").append(field.getCapacity());
         json.append(", \"string_capacity\": ").append(field.getStringCapacity());
         json.append(", \"nested_type_name\": ");
         appendJsonString(json, field.getNestedTypeName());
         json.append("}}");
      }
      json.append("]}");
   }

   /** JSON string with Python {@code json.dumps(ensure_ascii=True)} escaping. */
   private static void appendJsonString(StringBuilder json, String value)
   {
      json.append('"');
      for (int i = 0; i < value.length(); ++i)
      {
         char c = value.charAt(i);
         switch (c)
         {
            case '"' -> json.append("\\\"");
            case '\\' -> json.append("\\\\");
            case '\n' -> json.append("\\n");
            case '\r' -> json.append("\\r");
            case '\t' -> json.append("\\t");
            case '\b' -> json.append("\\b");
            case '\f' -> json.append("\\f");
            default ->
            {
               if (c < 0x20 || c > 0x7E)
                  json.append(String.format("\\u%04x", (int) c));
               else
                  json.append(c);
            }
         }
      }
      json.append('"');
   }

   /**
    * @return {@code "RIHS01_"} followed by the lowercase hexadecimal SHA-256 of the UTF-8 bytes of {@code json}
    */
   public static String hashOfJson(String json)
   {
      try
      {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder("RIHS01_");
         for (byte b : digest)
         {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
         }
         return hex.toString();
      }
      catch (NoSuchAlgorithmException e)
      {
         throw new IllegalStateException("SHA-256 is not available", e);
      }
   }
}
