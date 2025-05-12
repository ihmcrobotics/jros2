package us.ihmc.jros2;

import org.junit.jupiter.api.Test;
import us.ihmc.jros2.msg.Bool;

import static org.junit.jupiter.api.Assertions.*;

public class ROS2TopicTest
{
   @Test
   public void testEmptyConstructor()
   {
      ROS2Topic<?> topic = new ROS2Topic<>();

      assertEquals(0, topic.numberOfTokens());
      assertEquals("", topic.getName());
      assertNull(topic.getType());
   }

   @Test
   public void testNameConstructor()
   {
      // Test with empty string
      ROS2Topic<?> topic0 = new ROS2Topic<>("");
      assertEquals(0, topic0.numberOfTokens());
      assertEquals("", topic0.getName());
      assertNull(topic0.getType());

      // Test with only one segment
      ROS2Topic<?> topic1 = new ROS2Topic<>("/test_topic");
      assertEquals(1, topic1.numberOfTokens());
      assertEquals("/test_topic", topic1.getName());
      assertNull(topic1.getType());

      // Test with two segments
      ROS2Topic<?> topic2 = new ROS2Topic<>("/test_topic/sub_topic");
      assertEquals(2, topic2.numberOfTokens());
      assertEquals("/test_topic/sub_topic", topic2.getName());
      assertNull(topic2.getType());

      // Test with five segments
      ROS2Topic<?> topic5 = new ROS2Topic<>("/test_topic/sub_topic_1/sub_topic_2/sub_topic_3/sub_topic_4");
      assertEquals(5, topic5.numberOfTokens());
      assertEquals("/test_topic/sub_topic_1/sub_topic_2/sub_topic_3/sub_topic_4", topic5.getName());
      assertNull(topic5.getType());
   }

   @Test
   public void testNameClassConstructor()
   {
      ROS2Topic<Bool> boolTopic = new ROS2Topic<>("/bool_topic", Bool.class);
      assertEquals(1, boolTopic.numberOfTokens());
      assertEquals("/bool_topic", boolTopic.getName());
      assertEquals(Bool.class, boolTopic.getType());
   }

   @Test
   public void testAppendedWith()
   {
      // Create an empty topic
      ROS2Topic<?> topic = new ROS2Topic<>("");
      assertEquals(0, topic.numberOfTokens());
      assertEquals("", topic.getName());

      // Append nothing
      ROS2Topic<?> appendedTopic = topic.appendedWith(null);
      assertEquals(0, appendedTopic.numberOfTokens());
      assertEquals(topic.getName(), appendedTopic.getName());
      assertEquals(topic.getType(), appendedTopic.getType());
      assertNotSame(topic, appendedTopic);

      // Append empty string
      appendedTopic = topic.appendedWith("");
      assertEquals(0, appendedTopic.numberOfTokens());
      assertEquals(topic.getName(), appendedTopic.getName());
      assertEquals(topic.getType(), appendedTopic.getType());
      assertNotSame(topic, appendedTopic);

      // Append a segment
      appendedTopic = topic.appendedWith("test");
      assertEquals(1, appendedTopic.numberOfTokens());
      assertEquals("/test", appendedTopic.getName());
      assertEquals(topic.getType(), appendedTopic.getType());
      assertNotSame(topic, appendedTopic);

      // Append another segment
      appendedTopic = appendedTopic.appendedWith("another_test");
      assertEquals(2, appendedTopic.numberOfTokens());
      assertEquals("/test/another_test", appendedTopic.getName());
      assertEquals(topic.getType(), appendedTopic.getType());
      assertNotSame(topic, appendedTopic);

      // Append multiple segments
      appendedTopic = topic.appendedWith("test/another_test");
      assertEquals(2, appendedTopic.numberOfTokens());
      assertEquals("/test/another_test", appendedTopic.getName());
      assertEquals(topic.getType(), appendedTopic.getType());
      assertNotSame(topic, appendedTopic);
   }

   @Test
   public void testPrependedWith()
   {
      // Create an empty topic
      ROS2Topic<?> topic = new ROS2Topic<>("");
      assertEquals(0, topic.numberOfTokens());
      assertEquals("", topic.getName());

      // Prepend nothing
      ROS2Topic<?> prependedTopic = topic.prependedWith(null);
      assertEquals(0, prependedTopic.numberOfTokens());
      assertEquals(topic.getName(), prependedTopic.getName());
      assertEquals(topic.getType(), prependedTopic.getType());
      assertNotSame(topic, prependedTopic);

      // Prepend empty string
      prependedTopic = topic.prependedWith("");
      assertEquals(0, prependedTopic.numberOfTokens());
      assertEquals(topic.getName(), prependedTopic.getName());
      assertEquals(topic.getType(), prependedTopic.getType());
      assertNotSame(topic, prependedTopic);

      // Prepend a segment
      prependedTopic = topic.prependedWith("test");
      assertEquals(1, prependedTopic.numberOfTokens());
      assertEquals("/test", prependedTopic.getName());
      assertEquals(topic.getType(), prependedTopic.getType());
      assertNotSame(topic, prependedTopic);

      // Prepend another segment
      prependedTopic = prependedTopic.prependedWith("another_test");
      assertEquals(2, prependedTopic.numberOfTokens());
      assertEquals("/another_test/test", prependedTopic.getName());
      assertEquals(topic.getType(), prependedTopic.getType());
      assertNotSame(topic, prependedTopic);

      // Prepend multiple segments
      prependedTopic = topic.prependedWith("test/another_test");
      assertEquals(2, prependedTopic.numberOfTokens());
      assertEquals("/test/another_test", prependedTopic.getName());
      assertEquals(topic.getType(), prependedTopic.getType());
      assertNotSame(topic, prependedTopic);
   }

   @Test
   public void testInsertion()
   {
      ROS2Topic<?> startTopic = new ROS2Topic<>("/start/middle/end");

      ROS2Topic<?> insertNothing = startTopic.insert(1, "");
      assertEquals(startTopic.getName(), insertNothing.getName());

      insertNothing = startTopic.insert(1, null);
      assertEquals(startTopic.getName(), insertNothing.getName());

      ROS2Topic<?> prefixed = startTopic.insert(0, "prefix");
      assertEquals(4, prefixed.numberOfTokens());
      assertEquals("/prefix/start/middle/end", prefixed.getName());

      ROS2Topic<?> suffixed = prefixed.insert(prefixed.numberOfTokens(), "suffix");
      assertEquals(5, suffixed.numberOfTokens());
      assertEquals("/prefix/start/middle/end/suffix", suffixed.getName());

      ROS2Topic<?> startInsertMiddle = suffixed.insert(2, "insert");
      assertEquals(6, startInsertMiddle.numberOfTokens());
      assertEquals("/prefix/start/insert/middle/end/suffix", startInsertMiddle.getName());

      ROS2Topic<?> middleInsertEnd = startInsertMiddle.insert(4, "insert");
      assertEquals(7, middleInsertEnd.numberOfTokens());
      assertEquals("/prefix/start/insert/middle/insert/end/suffix", middleInsertEnd.getName());
   }

   @Test
   public void testWithType()
   {
      ROS2Topic<?> noType = new ROS2Topic<>("/test");
      assertNull(noType.getType());
      assertEquals("/test", noType.getName());

      ROS2Topic<Bool> boolType = noType.withType(Bool.class);
      assertEquals(Bool.class, boolType.getType());
      assertEquals("/test", boolType.getName());
   }
}
