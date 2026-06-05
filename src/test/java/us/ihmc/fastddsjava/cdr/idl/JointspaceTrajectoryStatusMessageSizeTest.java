package us.ihmc.fastddsjava.cdr.idl;

import org.junit.jupiter.api.Test;
import us.ihmc.fastddsjava.cdr.CDRBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies CDR size calculation for a message shaped like {@code JointspaceTrajectoryStatusMessage}.
 */
public class JointspaceTrajectoryStatusMessageSizeTest
{
   @Test
   public void testCalculateSizeBytesMatchesSerialize()
   {
      IDLStringSequence jointNames = new IDLStringSequence();
      jointNames.add("SpineYaw");
      jointNames.add("LeftShoulderPitch");
      jointNames.add("RightElbow");

      IDLDoubleSequence desiredJointPositions = new IDLDoubleSequence();
      desiredJointPositions.putAt(0, 0.1);
      desiredJointPositions.putAt(1, 0.2);
      desiredJointPositions.putAt(2, 0.3);

      IDLDoubleSequence actualJointPositions = new IDLDoubleSequence();
      actualJointPositions.putAt(0, 0.11);
      actualJointPositions.putAt(1, 0.21);
      actualJointPositions.putAt(2, 0.31);

      int calculatedBytes = calculateMessageSizeBytes(jointNames, desiredJointPositions, actualJointPositions);

      CDRBuffer buffer = new CDRBuffer();
      buffer.ensureRemainingCapacity(512);
      buffer.writePayloadHeader();
      int bodyStartPosition = buffer.getBufferUnsafe().position();
      serializeMessage(buffer, jointNames, desiredJointPositions, actualJointPositions);
      int serializedBytes = buffer.getBufferUnsafe().position() - bodyStartPosition;

      assertEquals(serializedBytes, calculatedBytes);
   }

   private static int calculateMessageSizeBytes(IDLStringSequence jointNames,
                                                IDLDoubleSequence desiredJointPositions,
                                                IDLDoubleSequence actualJointPositions)
   {
      int currentAlignment = 0;
      currentAlignment += 8 + CDRBuffer.alignment(currentAlignment, 8); // sequence_id
      currentAlignment += jointNames.calculateSizeBytes(currentAlignment);
      currentAlignment += 1 + CDRBuffer.alignment(currentAlignment, 1); // status byte
      currentAlignment += 8 + CDRBuffer.alignment(currentAlignment, 8); // timestamp
      currentAlignment += desiredJointPositions.calculateSizeBytes(currentAlignment);
      currentAlignment += actualJointPositions.calculateSizeBytes(currentAlignment);
      return currentAlignment;
   }

   private static void serializeMessage(CDRBuffer buffer,
                                        IDLStringSequence jointNames,
                                        IDLDoubleSequence desiredJointPositions,
                                        IDLDoubleSequence actualJointPositions)
   {
      buffer.writeLong(42L);
      jointNames.serialize(buffer);
      buffer.writeByte((byte) 1);
      buffer.writeDouble(1.5);
      desiredJointPositions.serialize(buffer);
      actualJointPositions.serialize(buffer);
   }
}
