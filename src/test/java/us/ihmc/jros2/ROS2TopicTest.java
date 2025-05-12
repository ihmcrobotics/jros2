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

      assertEquals(0, topic.numberOfSegments());
      assertEquals("", topic.getName());
      assertEquals("rt", topic.getFastDDSTopicName());
      assertNull(topic.getType());
   }

   @Test
   public void testNameConstructor()
   {
      // Test with empty string
      ROS2Topic<?> topic0 = new ROS2Topic<>("");
      assertEquals(0, topic0.numberOfSegments());
      assertEquals("", topic0.getName());
      assertEquals("rt", topic0.getFastDDSTopicName());
      assertNull(topic0.getType());

      // Test with only one segment
      ROS2Topic<?> topic1 = new ROS2Topic<>("/test_topic");
      assertEquals(1, topic1.numberOfSegments());
      assertEquals("/test_topic", topic1.getName());
      assertEquals("rt/test_topic", topic1.getFastDDSTopicName());
      assertNull(topic1.getType());

      // Test with two segments
      ROS2Topic<?> topic2 = new ROS2Topic<>("/test_topic/sub_topic");
      assertEquals(2, topic2.numberOfSegments());
      assertEquals("/test_topic/sub_topic", topic2.getName());
      assertEquals("rt/test_topic/sub_topic", topic2.getFastDDSTopicName());
      assertNull(topic2.getType());

      // Test with five segments
      ROS2Topic<?> topic5 = new ROS2Topic<>("/test_topic/sub_topic_1/sub_topic_2/sub_topic_3/sub_topic_4");
      assertEquals(5, topic5.numberOfSegments());
      assertEquals("/test_topic/sub_topic_1/sub_topic_2/sub_topic_3/sub_topic_4", topic5.getName());
      assertEquals("rt/test_topic/sub_topic_1/sub_topic_2/sub_topic_3/sub_topic_4", topic5.getFastDDSTopicName());
      assertNull(topic5.getType());
   }

   @Test
   public void testNameClassConstructor()
   {
      ROS2Topic<Bool> boolTopic = new ROS2Topic<>("/bool_topic", Bool.class);
      assertEquals(1, boolTopic.numberOfSegments());
      assertEquals("/bool_topic", boolTopic.getName());
      assertEquals("rt/bool_topic", boolTopic.getFastDDSTopicName());
      assertEquals(Bool.class, boolTopic.getType());
   }

   @Test
   public void testAppendedWith()
   {
      // Create an empty topic
      ROS2Topic<?> topic = new ROS2Topic<>("");
      assertEquals(0, topic.numberOfSegments());
      assertEquals("", topic.getName());
      assertEquals("rt", topic.getFastDDSTopicName());

      // Append nothing
      ROS2Topic<?> appendedTopic = topic.appendedWith(null);
      assertEquals(0, appendedTopic.numberOfSegments());
      assertEquals(topic.getName(), appendedTopic.getName());
      assertEquals(topic.getFastDDSTopicName(), appendedTopic.getFastDDSTopicName());
      assertEquals(topic.getType(), appendedTopic.getType());
      assertNotSame(topic, appendedTopic);

      // Append empty string
      appendedTopic = topic.appendedWith("");
      assertEquals(0, appendedTopic.numberOfSegments());
      assertEquals(topic.getName(), appendedTopic.getName());
      assertEquals(topic.getFastDDSTopicName(), appendedTopic.getFastDDSTopicName());
      assertEquals(topic.getType(), appendedTopic.getType());
      assertNotSame(topic, appendedTopic);

      // Append a segment
      appendedTopic = topic.appendedWith("test");
      assertEquals(1, appendedTopic.numberOfSegments());
      assertEquals("/test", appendedTopic.getName());
      assertEquals("rt/test", appendedTopic.getFastDDSTopicName());
      assertEquals(topic.getType(), appendedTopic.getType());
      assertNotSame(topic, appendedTopic);

      // Append another segment
      appendedTopic = appendedTopic.appendedWith("another_test");
      assertEquals(2, appendedTopic.numberOfSegments());
      assertEquals("/test/another_test", appendedTopic.getName());
      assertEquals("rt/test/another_test", appendedTopic.getFastDDSTopicName());
      assertEquals(topic.getType(), appendedTopic.getType());
      assertNotSame(topic, appendedTopic);

      // Append multiple segments
      appendedTopic = topic.appendedWith("test/another_test");
      assertEquals(2, appendedTopic.numberOfSegments());
      assertEquals("/test/another_test", appendedTopic.getName());
      assertEquals("rt/test/another_test", appendedTopic.getFastDDSTopicName());
      assertEquals(topic.getType(), appendedTopic.getType());
      assertNotSame(topic, appendedTopic);
   }

   @Test
   public void testPrependedWith()
   {
      // Create an empty topic
      ROS2Topic<?> topic = new ROS2Topic<>("");
      assertEquals(0, topic.numberOfSegments());
      assertEquals("", topic.getName());
      assertEquals("rt", topic.getFastDDSTopicName());

      // Prepend nothing
      ROS2Topic<?> prependedTopic = topic.prependedWith(null);
      assertEquals(0, prependedTopic.numberOfSegments());
      assertEquals(topic.getName(), prependedTopic.getName());
      assertEquals(topic.getFastDDSTopicName(), prependedTopic.getFastDDSTopicName());
      assertEquals(topic.getType(), prependedTopic.getType());
      assertNotSame(topic, prependedTopic);

      // Prepend empty string
      prependedTopic = topic.prependedWith("");
      assertEquals(0, prependedTopic.numberOfSegments());
      assertEquals(topic.getName(), prependedTopic.getName());
      assertEquals(topic.getFastDDSTopicName(), prependedTopic.getFastDDSTopicName());
      assertEquals(topic.getType(), prependedTopic.getType());
      assertNotSame(topic, prependedTopic);

      // Prepend a segment
      prependedTopic = topic.prependedWith("test");
      assertEquals(1, prependedTopic.numberOfSegments());
      assertEquals("/test", prependedTopic.getName());
      assertEquals("rt/test", prependedTopic.getFastDDSTopicName());
      assertEquals(topic.getType(), prependedTopic.getType());
      assertNotSame(topic, prependedTopic);

      // Prepend another segment
      prependedTopic = prependedTopic.prependedWith("another_test");
      assertEquals(2, prependedTopic.numberOfSegments());
      assertEquals("/another_test/test", prependedTopic.getName());
      assertEquals("rt/another_test/test", prependedTopic.getFastDDSTopicName());
      assertEquals(topic.getType(), prependedTopic.getType());
      assertNotSame(topic, prependedTopic);

      // Prepend multiple segments
      prependedTopic = topic.prependedWith("test/another_test");
      assertEquals(2, prependedTopic.numberOfSegments());
      assertEquals("/test/another_test", prependedTopic.getName());
      assertEquals("rt/test/another_test", prependedTopic.getFastDDSTopicName());
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
      assertEquals(4, prefixed.numberOfSegments());
      assertEquals("/prefix/start/middle/end", prefixed.getName());

      ROS2Topic<?> suffixed = prefixed.insert(prefixed.numberOfSegments(), "suffix");
      assertEquals(5, suffixed.numberOfSegments());
      assertEquals("/prefix/start/middle/end/suffix", suffixed.getName());

      ROS2Topic<?> startInsertMiddle = suffixed.insert(2, "insert");
      assertEquals(6, startInsertMiddle.numberOfSegments());
      assertEquals("/prefix/start/insert/middle/end/suffix", startInsertMiddle.getName());

      ROS2Topic<?> middleInsertEnd = startInsertMiddle.insert(4, "insert");
      assertEquals(7, middleInsertEnd.numberOfSegments());
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
