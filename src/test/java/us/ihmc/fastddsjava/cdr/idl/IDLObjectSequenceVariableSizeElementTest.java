package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.fastddsjava.cdr.CDRSerializable;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for a bug where {@link IDLSequence#calculateSizeBytes(int)} could return a value larger than
 * the number of bytes {@link IDLSequence#serialize(CDRBuffer)} actually writes, for an {@link IDLObjectSequence}
 * of elements whose size varies (e.g. because they contain a string field). {@link IDLObjectSequenceTest} does not
 * catch this because its {@code TestIDLMsg} fixture is a fixed 4 bytes per element - already a valid CDR alignment
 * boundary - so the bug this guards against never manifests there.
 */
public class IDLObjectSequenceVariableSizeElementTest
{
   /**
    * A struct whose encoded size varies with the name field's length, unlike {@code TestIDLMsg}'s fixed 4 bytes.
    * Mirrors the shape (an int followed by a string) that exposed the bug in a real generated message,
    * {@code geometry_msgs.TransformStamped} (nested under {@code tf2_msgs.TFMessage.transforms}).
    */
   static class VariableSizeMsg implements CDRSerializable
   {
      private int id;
      private final StringBuilder name = new StringBuilder();

      @Override
      public int calculateSizeBytes(int currentAlignment)
      {
         int initialAlignment = currentAlignment;
         currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // id
         currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4) + name.length() + 1; // name
         return currentAlignment - initialAlignment;
      }

      @Override
      public void serialize(CDRBuffer buffer)
      {
         buffer.writeInt(id);
         buffer.writeString(name);
      }

      @Override
      public void deserialize(CDRBuffer buffer)
      {
         id = buffer.readInt();
         buffer.readString(name);
      }
   }

   @Test
   public void testCalculateSizeBytesMatchesActualSerializedSize()
   {
      IDLObjectSequence<VariableSizeMsg> sequence = new IDLObjectSequence<>(VariableSizeMsg.class);

      VariableSizeMsg first = sequence.add();
      first.id = 1;
      first.name.append("pelvis"); // 6 chars -> element size is not a power of two

      VariableSizeMsg second = sequence.add();
      second.id = 2;
      second.name.append("thigh"); // 5 chars

      int calculatedSizeBytes = sequence.calculateSizeBytes(0);

      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(CDRBuffer.PAYLOAD_HEADER.length + calculatedSizeBytes);
      buffer.writePayloadHeader();
      sequence.serialize(buffer);

      int actualSizeBytes = buffer.getBufferUnsafe().position() - CDRBuffer.PAYLOAD_HEADER.length;

      assertEquals(actualSizeBytes,
                   calculatedSizeBytes,
                   "calculateSizeBytes() must equal the number of bytes serialize() actually writes - "
                   + "callers such as ROS2Publisher#writeAndPublish previously trusted it as the exact payload "
                   + "length written to the wire.");
   }

   @Test
   public void testDeserializeRoundTrip()
   {
      IDLObjectSequence<VariableSizeMsg> sequence = new IDLObjectSequence<>(VariableSizeMsg.class);
      sequence.add().id = 42;
      sequence.get(0).name.append("longer_frame_name_example");
      sequence.add().id = 7;
      sequence.get(1).name.append("x");

      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(CDRBuffer.PAYLOAD_HEADER.length + sequence.calculateSizeBytes(0));
      buffer.writePayloadHeader();
      sequence.serialize(buffer);
      buffer.rewind();
      buffer.readPayloadHeader();

      IDLObjectSequence<VariableSizeMsg> deserialized = new IDLObjectSequence<>(VariableSizeMsg.class);
      deserialized.deserialize(buffer);

      assertEquals(2, deserialized.size());
      assertEquals(42, deserialized.get(0).id);
      assertEquals("longer_frame_name_example", deserialized.get(0).name.toString());
      assertEquals(7, deserialized.get(1).id);
      assertEquals("x", deserialized.get(1).name.toString());
   }
}
