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
package us.ihmc.jros2;

import java.nio.charset.StandardCharsets;

/**
 * ROS 2 name validation and expansion, mirroring rmw ({@code rmw_validate_node_name}, {@code rmw_validate_namespace},
 * {@code rmw_validate_full_topic_name}) and rcl ({@code rcl_validate_topic_name}, {@code rcl_expand_topic_name},
 * namespace normalization in {@code rcl_node_init}).
 * <p>
 * The checks run in the same order as the C implementations, so the first reported problem and its index are the
 * ones rmw/rcl would report. Indexes are byte indexes into the UTF-8 encoding of the name; any non-ASCII byte is an
 * unallowed character.
 * <p>
 * See <a href="https://design.ros2.org/articles/topic_and_service_names.html">Topic and Service name mapping to DDS</a>.
 */
final class ROS2Names
{
   /** {@code RMW_NODE_NAME_MAX_NAME_LENGTH} */
   static final int NODE_NAME_MAX_LENGTH = 255;
   /** {@code RMW_TOPIC_MAX_NAME_LENGTH}: 255 minus 8 characters reserved for DDS prefixes such as "rt" or "rq". */
   static final int TOPIC_NAME_MAX_LENGTH = 255 - 8;
   /** {@code RMW_NAMESPACE_MAX_LENGTH} */
   static final int NAMESPACE_MAX_LENGTH = TOPIC_NAME_MAX_LENGTH - 2;

   /** The DDS topic name prefix for ROS topics. */
   static final String TOPIC_PREFIX = "rt";

   private ROS2Names()
   {
   }

   /**
    * The outcome of a failed validation: a reason (the rmw/rcl message, byte for byte) and the byte index of the problem.
    */
   static final class Invalid
   {
      final String reason;
      final int index;
      /** Extra explanation appended by {@link #toString()}, e.g. the numeric limit that the rmw message names by macro. */
      final String detail;

      Invalid(String reason, int index)
      {
         this(reason, index, null);
      }

      Invalid(String reason, int index, String detail)
      {
         this.reason = reason;
         this.index = index;
         this.detail = detail;
      }

      @Override
      public String toString()
      {
         return reason + (detail == null ? "" : " (" + detail + ")") + " (at index " + index + ")";
      }
   }

   /**
    * {@code rmw_validate_node_name}.
    *
    * @return null if valid
    */
   static Invalid validateNodeName(String nodeName)
   {
      byte[] name = utf8(nodeName);
      if (name.length == 0)
      {
         return new Invalid("node name must not be empty", 0);
      }
      for (int i = 0; i < name.length; ++i)
      {
         if (!isAlnum(name[i]) && name[i] != '_')
         {
            return new Invalid("node name must not contain characters other than alphanumerics or '_'", i);
         }
      }
      if (isDigit(name[0]))
      {
         return new Invalid("node name must not start with a number", 0);
      }
      if (name.length > NODE_NAME_MAX_LENGTH)
      {
         return new Invalid("node name length should not exceed '" + NODE_NAME_MAX_LENGTH + "'", NODE_NAME_MAX_LENGTH - 1);
      }
      return null;
   }

   /**
    * {@code rmw_validate_namespace}. The namespace must already be normalized (see {@link #normalizeNamespace(String)}).
    *
    * @return null if valid
    */
   static Invalid validateNamespace(String namespace)
   {
      byte[] ns = utf8(namespace);
      if (ns.length == 0)
      {
         return new Invalid("namespace must not be empty", 0);
      }
      if (ns.length == 1 && ns[0] == '/')
      {
         return null; // The root namespace is a special case
      }
      FullTopicNameCheck topicCheck = checkFullTopicName(ns);
      if (topicCheck != null && topicCheck != FullTopicNameCheck.TOO_LONG)
      {
         return new Invalid(topicCheck.namespaceReason, topicCheck.index(ns));
      }
      if (ns.length > NAMESPACE_MAX_LENGTH)
      {
         // rmw prints the macro name, not its value
         return new Invalid("namespace should not exceed 'RMW_NAMESPACE_MAX_NAME_LENGTH'", NAMESPACE_MAX_LENGTH - 1, "at most " + NAMESPACE_MAX_LENGTH + " bytes");
      }
      return null;
   }

   /**
    * {@code rmw_validate_full_topic_name}.
    *
    * @return null if valid
    */
   static Invalid validateFullTopicName(String topicName)
   {
      byte[] name = utf8(topicName);
      FullTopicNameCheck check = checkFullTopicName(name);
      if (check == null)
      {
         return null;
      }
      return new Invalid(check.topicReason, check.index(name), check == FullTopicNameCheck.TOO_LONG ? "at most " + TOPIC_NAME_MAX_LENGTH + " bytes" : null);
   }

   /**
    * {@code rcl_validate_topic_name}: validates a topic name before expansion. Relative names, a leading {@code ~} and
    * {@code {substitutions}} are allowed.
    *
    * @return null if valid
    */
   static Invalid validateTopicName(String topicName)
   {
      byte[] name = utf8(topicName);
      int length = name.length;
      if (length == 0)
      {
         return new Invalid("topic name must not be empty string", 0);
      }
      if (isDigit(name[0]))
      {
         return new Invalid("topic name token must not start with a number", 0);
      }
      if (name[length - 1] == '/')
      {
         return new Invalid("topic name must not end with a forward slash", length - 1);
      }

      boolean inOpenCurlyBrace = false;
      int openingCurlyBraceIndex = 0;
      for (int i = 0; i < length; ++i)
      {
         byte c = name[i];
         if (isAlnum(c))
         {
            if (isDigit(c) && inOpenCurlyBrace && i > 0 && i - 1 == openingCurlyBraceIndex)
            {
               return new Invalid("substitution name must not start with a number", i);
            }
         }
         else if (c == '_')
         {
            // allowed
         }
         else if (c == '/')
         {
            if (inOpenCurlyBrace)
            {
               return new Invalid(SUBSTITUTION_UNALLOWED_CHARACTERS, i);
            }
         }
         else if (c == '~')
         {
            if (i != 0)
            {
               return new Invalid("topic name must not have tilde '~' unless it is the first character", i);
            }
         }
         else if (c == '{')
         {
            openingCurlyBraceIndex = i;
            if (inOpenCurlyBrace)
            {
               return new Invalid(SUBSTITUTION_UNALLOWED_CHARACTERS, i);
            }
            inOpenCurlyBrace = true;
         }
         else if (c == '}')
         {
            if (!inOpenCurlyBrace)
            {
               return new Invalid(UNMATCHED_CURLY_BRACE, i);
            }
            inOpenCurlyBrace = false;
         }
         else
         {
            return new Invalid(inOpenCurlyBrace ? SUBSTITUTION_UNALLOWED_CHARACTERS : "topic name must not contain characters other than alphanumerics, '_', '~', '{', or '}'",
                               i);
         }
      }
      if (inOpenCurlyBrace)
      {
         return new Invalid(UNMATCHED_CURLY_BRACE, openingCurlyBraceIndex);
      }

      for (int i = 0; i < length - 1; ++i)
      {
         if (name[i] == '/')
         {
            if (isDigit(name[i + 1]))
            {
               return new Invalid("topic name token must not start with a number", i + 1);
            }
         }
         else if (i == 1 && name[0] == '~')
         {
            // rcl quirk kept on purpose: a two-character "~x" never reaches this check and is accepted
            return new Invalid("topic name must not have a tilde '~' that is not followed by a forward slash '/'", i);
         }
      }
      return null;
   }

   /**
    * Normalizes a node namespace the way {@code rcl_node_init} does: null or empty becomes {@code "/"}, and a namespace
    * that does not start with {@code /} gets one prepended. The result still has to be validated.
    */
   static String normalizeNamespace(String namespace)
   {
      if (namespace == null || namespace.isEmpty())
      {
         return "/";
      }
      if (namespace.charAt(0) != '/')
      {
         return "/" + namespace;
      }
      return namespace;
   }

   /**
    * Validates a node name and throws an {@link IllegalArgumentException} with the rmw reason if it is invalid.
    */
   static void checkNodeName(String nodeName)
   {
      if (nodeName == null)
      {
         throw new IllegalArgumentException("Invalid node name: the node name must not be null");
      }
      Invalid invalid = validateNodeName(nodeName);
      if (invalid != null)
      {
         throw new IllegalArgumentException("Invalid node name '" + nodeName + "': " + invalid);
      }
   }

   /**
    * Normalizes and validates a node namespace.
    *
    * @return the normalized namespace
    * @throws IllegalArgumentException with the rmw reason if the namespace is invalid
    */
   static String checkNamespace(String namespace)
   {
      String normalized = normalizeNamespace(namespace);
      Invalid invalid = validateNamespace(normalized);
      if (invalid != null)
      {
         throw new IllegalArgumentException("Invalid node namespace '" + namespace + "': " + invalid);
      }
      return normalized;
   }

   /**
    * @return the fully qualified node name, e.g. "/talker" or "/robot/talker"
    */
   static String fullyQualifiedNodeName(String namespace, String nodeName)
   {
      return namespace.endsWith("/") ? namespace + nodeName : namespace + "/" + nodeName;
   }

   /**
    * {@code rcl_expand_topic_name} with rcl's default (empty) substitution map: expands a relative or private topic name
    * against the node name and namespace. The result is not validated; use {@link #resolveTopicName} for that.
    *
    * @throws IllegalArgumentException if the input, node name or namespace is invalid, or if the name uses an unknown
    *                                  substitution
    */
   static String expandTopicName(String topicName, String nodeName, String namespace)
   {
      if (topicName == null)
      {
         throw new IllegalArgumentException("Invalid topic name: the topic name must not be null");
      }
      Invalid invalid = validateTopicName(topicName);
      if (invalid != null)
      {
         throw new IllegalArgumentException("Invalid topic name '" + topicName + "': " + invalid);
      }
      checkNodeName(nodeName);
      invalid = validateNamespace(namespace);
      if (invalid != null)
      {
         throw new IllegalArgumentException("Invalid node namespace '" + namespace + "': " + invalid);
      }

      boolean hasSubstitution = topicName.indexOf('{') >= 0;
      boolean hasNamespaceTilde = topicName.charAt(0) == '~';
      boolean isAbsolute = topicName.charAt(0) == '/';
      if (isAbsolute && !hasSubstitution)
      {
         return topicName;
      }

      String output = topicName;
      boolean rootNamespace = namespace.length() == 1;
      if (hasNamespaceTilde)
      {
         output = namespace + (rootNamespace ? "" : "/") + nodeName + topicName.substring(1);
      }

      if (hasSubstitution)
      {
         int opening;
         while ((opening = output.indexOf('{')) >= 0)
         {
            int closing = output.indexOf('}');
            String substitution = output.substring(opening, closing + 1);
            String replacement;
            switch (substitution)
            {
               case "{node}":
                  replacement = nodeName;
                  break;
               case "{ns}":
               case "{namespace}":
                  replacement = namespace;
                  break;
               default:
                  throw new IllegalArgumentException("Invalid topic name '" + topicName + "': unknown substitution: " + substitution);
            }
            output = output.replace(substitution, replacement);
         }
      }

      if (output.charAt(0) != '/')
      {
         output = namespace + (rootNamespace ? "" : "/") + output;
      }
      return output;
   }

   /**
    * Expands a topic name like {@code rcl_node_resolve_name} (without remapping) and validates the result like
    * {@code rmw_validate_full_topic_name}.
    *
    * @return the fully qualified ROS topic name, e.g. "/robot/chatter"
    * @throws IllegalArgumentException with the rmw/rcl reason and index if the name is invalid
    */
   static String resolveTopicName(String topicName, String nodeName, String namespace)
   {
      String expanded = expandTopicName(topicName, nodeName, namespace);
      Invalid invalid = validateFullTopicName(expanded);
      if (invalid != null)
      {
         if (expanded.equals(topicName))
         {
            throw new IllegalArgumentException("Invalid topic name '" + topicName + "': " + invalid);
         }
         throw new IllegalArgumentException("Invalid topic name '" + topicName + "' (expanded to '" + expanded + "'): " + invalid);
      }
      return expanded;
   }

   /**
    * @return the DDS topic name of a fully qualified ROS topic, e.g. "rt/chatter"
    */
   static String ddsTopicName(String fullyQualifiedTopicName)
   {
      return TOPIC_PREFIX + fullyQualifiedTopicName;
   }

   private static final String SUBSTITUTION_UNALLOWED_CHARACTERS = "substitution name must not contain characters other than alphanumerics or '_'";
   private static final String UNMATCHED_CURLY_BRACE = "topic name must not have unmatched (unbalanced) curly braces '{}'";

   /**
    * Results of {@code rmw_validate_full_topic_name}, with the reason strings of both the topic and the namespace
    * variants.
    */
   private enum FullTopicNameCheck
   {
      EMPTY("topic name must not be empty", "namespace must not be empty"),
      NOT_ABSOLUTE("topic name must be absolute, it must lead with a '/'", "namespace must be absolute, it must lead with a '/'"),
      ENDS_WITH_FORWARD_SLASH("topic name must not end with a '/'", "namespace must not end with a '/', unless only a '/'"),
      CONTAINS_UNALLOWED_CHARACTERS("topic name must not contain characters other than alphanumerics, '_', or '/'",
                                    "namespace must not contain characters other than alphanumerics, '_', or '/'"),
      CONTAINS_REPEATED_FORWARD_SLASH("topic name must not contain repeated '/'", "namespace must not contain repeated '/'"),
      NAME_TOKEN_STARTS_WITH_NUMBER("topic name must not have a token that starts with a number",
                                    "namespace must not have a token that starts with a number"),
      // rmw prints the macro expansion "255U - 8U", not its value
      TOO_LONG("topic length should not exceed '255U - 8U'", null);

      final String topicReason;
      final String namespaceReason;

      FullTopicNameCheck(String topicReason, String namespaceReason)
      {
         this.topicReason = topicReason;
         this.namespaceReason = namespaceReason;
      }

      /** Recomputes the index rmw reports for this result. */
      int index(byte[] name)
      {
         switch (this)
         {
            case EMPTY:
            case NOT_ABSOLUTE:
               return 0;
            case ENDS_WITH_FORWARD_SLASH:
               return name.length - 1;
            case CONTAINS_UNALLOWED_CHARACTERS:
               for (int i = 0; i < name.length; ++i)
               {
                  if (!isAlnum(name[i]) && name[i] != '_' && name[i] != '/')
                  {
                     return i;
                  }
               }
               return 0;
            case CONTAINS_REPEATED_FORWARD_SLASH:
            case NAME_TOKEN_STARTS_WITH_NUMBER:
               for (int i = 0; i < name.length - 1; ++i)
               {
                  if (name[i] == '/' && (name[i + 1] == '/' || isDigit(name[i + 1])))
                  {
                     return i + 1;
                  }
               }
               return 0;
            default:
               return TOPIC_NAME_MAX_LENGTH - 1;
         }
      }
   }

   private static FullTopicNameCheck checkFullTopicName(byte[] name)
   {
      int length = name.length;
      if (length == 0)
      {
         return FullTopicNameCheck.EMPTY;
      }
      if (name[0] != '/')
      {
         return FullTopicNameCheck.NOT_ABSOLUTE;
      }
      if (name[length - 1] == '/')
      {
         return FullTopicNameCheck.ENDS_WITH_FORWARD_SLASH;
      }
      for (int i = 0; i < length; ++i)
      {
         if (!isAlnum(name[i]) && name[i] != '_' && name[i] != '/')
         {
            return FullTopicNameCheck.CONTAINS_UNALLOWED_CHARACTERS;
         }
      }
      for (int i = 0; i < length - 1; ++i)
      {
         if (name[i] == '/')
         {
            if (name[i + 1] == '/')
            {
               return FullTopicNameCheck.CONTAINS_REPEATED_FORWARD_SLASH;
            }
            if (isDigit(name[i + 1]))
            {
               return FullTopicNameCheck.NAME_TOKEN_STARTS_WITH_NUMBER;
            }
         }
      }
      if (length > TOPIC_NAME_MAX_LENGTH)
      {
         return FullTopicNameCheck.TOO_LONG;
      }
      return null;
   }

   private static byte[] utf8(String value)
   {
      return value.getBytes(StandardCharsets.UTF_8);
   }

   private static boolean isDigit(byte c)
   {
      return c >= '0' && c <= '9';
   }

   private static boolean isAlnum(byte c)
   {
      return isDigit(c) || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
   }
}
