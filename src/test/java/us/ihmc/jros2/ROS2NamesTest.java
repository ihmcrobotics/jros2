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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Name validation and expansion. Every expected value below was produced by the real rmw/rcl C validators (through
 * rclpy) on Humble, Jazzy, Kilted and Lyrical.
 */
public class ROS2NamesTest
{
   private static String repeat(char c, int count)
   {
      StringBuilder builder = new StringBuilder(count);
      for (int i = 0; i < count; ++i)
      {
         builder.append(c);
      }
      return builder.toString();
   }

   private static void assertValid(ROS2Names.Invalid result)
   {
      assertNull(result, () -> "expected valid, got " + result);
   }

   private static void assertInvalid(ROS2Names.Invalid result, String reason, int index)
   {
      assertNotNull(result, "expected invalid");
      assertEquals(reason, result.reason);
      assertEquals(index, result.index);
   }

   private static final String NODE_UNALLOWED = "node name must not contain characters other than alphanumerics or '_'";

   @Test
   public void testValidateNodeName()
   {
      assertValid(ROS2Names.validateNodeName("talker"));
      assertValid(ROS2Names.validateNodeName("_hidden"));
      assertValid(ROS2Names.validateNodeName("a1"));
      assertValid(ROS2Names.validateNodeName("A_Z_09"));
      assertValid(ROS2Names.validateNodeName("__"));
      assertValid(ROS2Names.validateNodeName("_"));
      assertValid(ROS2Names.validateNodeName(repeat('x', 255)));

      assertInvalid(ROS2Names.validateNodeName("1a"), "node name must not start with a number", 0);
      assertInvalid(ROS2Names.validateNodeName(""), "node name must not be empty", 0);
      assertInvalid(ROS2Names.validateNodeName("a-b"), NODE_UNALLOWED, 1);
      assertInvalid(ROS2Names.validateNodeName("a/b"), NODE_UNALLOWED, 1);
      assertInvalid(ROS2Names.validateNodeName("a.b"), NODE_UNALLOWED, 1);
      assertInvalid(ROS2Names.validateNodeName("a b"), NODE_UNALLOWED, 1);
      assertInvalid(ROS2Names.validateNodeName("é"), NODE_UNALLOWED, 0);
      // The character check runs before the leading digit check
      assertInvalid(ROS2Names.validateNodeName("1-"), NODE_UNALLOWED, 1);
      assertInvalid(ROS2Names.validateNodeName(repeat('x', 256)), "node name length should not exceed '255'", 254);
   }

   @Test
   public void testValidateNamespace()
   {
      assertValid(ROS2Names.validateNamespace("/"));
      assertValid(ROS2Names.validateNamespace("/foo"));
      assertValid(ROS2Names.validateNamespace("/foo_1/b_2"));
      assertValid(ROS2Names.validateNamespace("/_hidden"));
      assertValid(ROS2Names.validateNamespace("/" + repeat('x', 244)));

      assertInvalid(ROS2Names.validateNamespace(""), "namespace must not be empty", 0);
      assertInvalid(ROS2Names.validateNamespace("foo"), "namespace must be absolute, it must lead with a '/'", 0);
      assertInvalid(ROS2Names.validateNamespace("/foo/"), "namespace must not end with a '/', unless only a '/'", 4);
      assertInvalid(ROS2Names.validateNamespace("/foo//bar"), "namespace must not contain repeated '/'", 5);
      assertInvalid(ROS2Names.validateNamespace("/1foo"), "namespace must not have a token that starts with a number", 1);
      assertInvalid(ROS2Names.validateNamespace("/foo/1bar"), "namespace must not have a token that starts with a number", 5);
      String unallowed = "namespace must not contain characters other than alphanumerics, '_', or '/'";
      assertInvalid(ROS2Names.validateNamespace("/foo-bar"), unallowed, 4);
      assertInvalid(ROS2Names.validateNamespace("/~"), unallowed, 1);
      assertInvalid(ROS2Names.validateNamespace("/{ns}"), unallowed, 1);
      // Characters are checked before repeated slashes
      assertInvalid(ROS2Names.validateNamespace("/a//b-"), unallowed, 5);
      // rmw names the limit by its macro; the jros2 message adds the value
      assertInvalid(ROS2Names.validateNamespace("/" + repeat('x', 245)), "namespace should not exceed 'RMW_NAMESPACE_MAX_NAME_LENGTH'", 244);
      assertTrue(ROS2Names.validateNamespace("/" + repeat('x', 245)).toString().contains("at most 245 bytes"));
      // Longer than a topic name may be: still reported as a namespace length problem
      assertInvalid(ROS2Names.validateNamespace("/" + repeat('x', 300)), "namespace should not exceed 'RMW_NAMESPACE_MAX_NAME_LENGTH'", 244);
   }

   @Test
   public void testValidateFullTopicName()
   {
      assertValid(ROS2Names.validateFullTopicName("/chatter"));
      assertValid(ROS2Names.validateFullTopicName("/a1/_b"));
      assertValid(ROS2Names.validateFullTopicName("/rt/x"));
      assertValid(ROS2Names.validateFullTopicName("/_hidden/x"));
      assertValid(ROS2Names.validateFullTopicName("/" + repeat('x', 246)));

      assertInvalid(ROS2Names.validateFullTopicName(""), "topic name must not be empty", 0);
      assertInvalid(ROS2Names.validateFullTopicName("chatter"), "topic name must be absolute, it must lead with a '/'", 0);
      assertInvalid(ROS2Names.validateFullTopicName("/"), "topic name must not end with a '/'", 0);
      assertInvalid(ROS2Names.validateFullTopicName("/a/"), "topic name must not end with a '/'", 2);
      // Trailing slash wins over repeated slashes
      assertInvalid(ROS2Names.validateFullTopicName("/a//"), "topic name must not end with a '/'", 3);
      assertInvalid(ROS2Names.validateFullTopicName("/a//b"), "topic name must not contain repeated '/'", 3);
      assertInvalid(ROS2Names.validateFullTopicName("/a//1"), "topic name must not contain repeated '/'", 3);
      assertInvalid(ROS2Names.validateFullTopicName("/1a"), "topic name must not have a token that starts with a number", 1);
      assertInvalid(ROS2Names.validateFullTopicName("/a/1b"), "topic name must not have a token that starts with a number", 3);
      String unallowed = "topic name must not contain characters other than alphanumerics, '_', or '/'";
      assertInvalid(ROS2Names.validateFullTopicName("/a~"), unallowed, 2);
      assertInvalid(ROS2Names.validateFullTopicName("/{a}"), unallowed, 1);
      assertInvalid(ROS2Names.validateFullTopicName("/a-b"), unallowed, 2);
      assertInvalid(ROS2Names.validateFullTopicName("/1-"), unallowed, 2);
      assertInvalid(ROS2Names.validateFullTopicName("/a.b"), unallowed, 2);
      // Indexes are UTF-8 byte indexes
      assertInvalid(ROS2Names.validateFullTopicName("/é"), unallowed, 1);
      assertInvalid(ROS2Names.validateFullTopicName("/" + repeat('x', 247)), "topic length should not exceed '255U - 8U'", 246);
      assertTrue(ROS2Names.validateFullTopicName("/" + repeat('x', 247)).toString().contains("at most 247 bytes"));
   }

   @Test
   public void testValidateTopicName()
   {
      assertValid(ROS2Names.validateTopicName("chatter"));
      assertValid(ROS2Names.validateTopicName("~"));
      assertValid(ROS2Names.validateTopicName("~/a"));
      assertValid(ROS2Names.validateTopicName("~a")); // rcl quirk
      assertValid(ROS2Names.validateTopicName("{node}"));
      assertValid(ROS2Names.validateTopicName("a//b")); // caught after expansion
      assertValid(ROS2Names.validateTopicName("/a"));
      assertValid(ROS2Names.validateTopicName("{a}{b}"));

      assertInvalid(ROS2Names.validateTopicName(""), "topic name must not be empty string", 0);
      assertInvalid(ROS2Names.validateTopicName("a~"), "topic name must not have tilde '~' unless it is the first character", 1);
      assertInvalid(ROS2Names.validateTopicName("~ab"), "topic name must not have a tilde '~' that is not followed by a forward slash '/'", 1);
      assertInvalid(ROS2Names.validateTopicName("{4x}"), "substitution name must not start with a number", 1);
      assertInvalid(ROS2Names.validateTopicName("{a/b}"), "substitution name must not contain characters other than alphanumerics or '_'", 2);
      assertInvalid(ROS2Names.validateTopicName("{a.b}"), "substitution name must not contain characters other than alphanumerics or '_'", 2);
      assertInvalid(ROS2Names.validateTopicName("{a"), "topic name must not have unmatched (unbalanced) curly braces '{}'", 0);
      assertInvalid(ROS2Names.validateTopicName("a}"), "topic name must not have unmatched (unbalanced) curly braces '{}'", 1);
      assertInvalid(ROS2Names.validateTopicName("a/"), "topic name must not end with a forward slash", 1);
      assertInvalid(ROS2Names.validateTopicName("1a"), "topic name token must not start with a number", 0);
      assertInvalid(ROS2Names.validateTopicName("a/1b"), "topic name token must not start with a number", 2);
      assertInvalid(ROS2Names.validateTopicName("{{a}}"), "substitution name must not contain characters other than alphanumerics or '_'", 1);
      assertInvalid(ROS2Names.validateTopicName("a.b"), "topic name must not contain characters other than alphanumerics, '_', '~', '{', or '}'", 1);
   }

   @Test
   public void testNormalizeNamespace()
   {
      assertEquals("/", ROS2Names.normalizeNamespace(null));
      assertEquals("/", ROS2Names.normalizeNamespace(""));
      assertEquals("/foo", ROS2Names.normalizeNamespace("foo"));
      assertEquals("/foo/bar", ROS2Names.normalizeNamespace("foo/bar"));
      assertEquals("/foo", ROS2Names.normalizeNamespace("/foo"));
      assertEquals("/", ROS2Names.normalizeNamespace("/"));

      assertEquals("/foo", ROS2Names.checkNamespace("foo"));
      assertEquals("/", ROS2Names.checkNamespace(null));
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ROS2Names.checkNamespace("/foo/"));
      assertTrue(e.getMessage().contains("namespace must not end with a '/', unless only a '/'"), e.getMessage());
      assertTrue(e.getMessage().contains("index 4"), e.getMessage());
      assertThrows(IllegalArgumentException.class, () -> ROS2Names.checkNamespace("foo-bar"));
   }

   @Test
   public void testCheckNodeName()
   {
      ROS2Names.checkNodeName("talker");
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ROS2Names.checkNodeName("my-node"));
      assertTrue(e.getMessage().contains("my-node"), e.getMessage());
      assertTrue(e.getMessage().contains(NODE_UNALLOWED), e.getMessage());
      assertTrue(e.getMessage().contains("index 2"), e.getMessage());
      assertThrows(IllegalArgumentException.class, () -> ROS2Names.checkNodeName(null));
      assertThrows(IllegalArgumentException.class, () -> ROS2Names.checkNodeName(""));
   }

   @Test
   public void testFullyQualifiedNodeName()
   {
      assertEquals("/talker", ROS2Names.fullyQualifiedNodeName("/", "talker"));
      assertEquals("/robot/talker", ROS2Names.fullyQualifiedNodeName("/robot", "talker"));
   }

   @Test
   public void testExpandTopicName()
   {
      String node = "my_node";
      assertEquals("/chatter", ROS2Names.expandTopicName("chatter", node, "/"));
      assertEquals("/ns/chatter", ROS2Names.expandTopicName("chatter", node, "/ns"));
      assertEquals("/chatter", ROS2Names.expandTopicName("/chatter", node, "/ns"));
      assertEquals("/my_node", ROS2Names.expandTopicName("~", node, "/"));
      assertEquals("/ns/my_node", ROS2Names.expandTopicName("~", node, "/ns"));
      assertEquals("/my_node/foo", ROS2Names.expandTopicName("~/foo", node, "/"));
      assertEquals("/a/b/my_node/foo", ROS2Names.expandTopicName("~/foo", node, "/a/b"));
      assertEquals("/ns/my_node", ROS2Names.expandTopicName("{node}", node, "/ns"));
      assertEquals("/my_node/x", ROS2Names.expandTopicName("{node}/x", node, "/"));
      assertEquals("/ns", ROS2Names.expandTopicName("{ns}", node, "/ns"));
      assertEquals("/ns/x", ROS2Names.expandTopicName("{namespace}/x", node, "/ns"));
      assertEquals("//x", ROS2Names.expandTopicName("{ns}/x", node, "/"));
      assertEquals("/my_node/x", ROS2Names.expandTopicName("/{node}/x", node, "/ns"));
      assertEquals("/a//b", ROS2Names.expandTopicName("a//b", node, "/"));
      assertEquals("/n/foo/my_node/my_node", ROS2Names.expandTopicName("foo/{node}/{node}", node, "/n"));
      assertEquals("/a/my_node//a", ROS2Names.expandTopicName("~/{ns}", node, "/a"));
      assertEquals("/my_nodea", ROS2Names.expandTopicName("~a", node, "/")); // rcl quirk

      IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> ROS2Names.expandTopicName("{unknown}", node, "/"));
      assertTrue(unknown.getMessage().contains("unknown substitution: {unknown}"), unknown.getMessage());
      assertThrows(IllegalArgumentException.class, () -> ROS2Names.expandTopicName("a.b", node, "/"));
      assertThrows(IllegalArgumentException.class, () -> ROS2Names.expandTopicName("chatter", "bad-node", "/"));
      assertThrows(IllegalArgumentException.class, () -> ROS2Names.expandTopicName("chatter", node, "/bad/"));
      assertThrows(IllegalArgumentException.class, () -> ROS2Names.expandTopicName(null, node, "/"));
   }

   @Test
   public void testResolveTopicName()
   {
      assertEquals("/robot/chatter", ROS2Names.resolveTopicName("chatter", "talker", "/robot"));
      assertEquals("/robot/talker/status", ROS2Names.resolveTopicName("~/status", "talker", "/robot"));
      assertEquals("/chatter", ROS2Names.resolveTopicName("/chatter", "talker", "/robot"));

      IllegalArgumentException repeated = assertThrows(IllegalArgumentException.class, () -> ROS2Names.resolveTopicName("{ns}/x", "talker", "/"));
      assertTrue(repeated.getMessage().contains("expanded to '//x'"), repeated.getMessage());
      assertTrue(repeated.getMessage().contains("topic name must not contain repeated '/'"), repeated.getMessage());
      assertTrue(repeated.getMessage().contains("index 1"), repeated.getMessage());

      IllegalArgumentException hyphen = assertThrows(IllegalArgumentException.class, () -> ROS2Names.resolveTopicName("/my-topic", "talker", "/"));
      assertTrue(hyphen.getMessage().contains("'/my-topic'"), hyphen.getMessage());
      assertTrue(hyphen.getMessage().contains("index 3"), hyphen.getMessage());

      assertThrows(IllegalArgumentException.class, () -> ROS2Names.resolveTopicName("", "talker", "/"));
      assertThrows(IllegalArgumentException.class, () -> ROS2Names.resolveTopicName("/", "talker", "/"));
      assertThrows(IllegalArgumentException.class, () -> ROS2Names.resolveTopicName("/" + repeat('x', 247), "talker", "/"));
      assertEquals("/" + repeat('x', 246), ROS2Names.resolveTopicName("/" + repeat('x', 246), "talker", "/"));
   }

   @Test
   public void testDdsTopicName()
   {
      assertEquals("rt/chatter", ROS2Names.ddsTopicName("/chatter"));
      assertEquals("rt/robot/chatter", ROS2Names.ddsTopicName("/robot/chatter"));
   }
}
