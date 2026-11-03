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

import us.ihmc.fastddsjava.cdr.CDRBuffer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hand-written CDR (XCDR1) message classes for the {@code rmw_dds_common} graph messages that rmw publishes on the
 * {@code ros_discovery_info} DDS topic:
 * <pre>
 * Gid.msg:                      char[16] data          (char[24] data on Humble and older)
 * NodeEntitiesInfo.msg:         string&lt;=256 node_namespace, string&lt;=256 node_name, Gid[] reader_gid_seq, Gid[] writer_gid_seq
 * ParticipantEntitiesInfo.msg:  Gid gid, NodeEntitiesInfo[] node_entities_info_seq
 * </pre>
 * The GID size is a property of each instance because Humble (24 bytes) and Iron and newer (16 bytes) are not wire
 * compatible. All padding is written as zero bytes, so serialization is deterministic.
 */
final class RmwDdsCommonMessages
{
   /** GID size of rmw_dds_common on Iron and newer ({@code RMW_GID_STORAGE_SIZE}). */
   static final int GID_SIZE_IRON_PLUS = 16;
   /** GID size of rmw_dds_common on Humble and older. */
   static final int GID_SIZE_HUMBLE = 24;
   /** Number of significant GID bytes: the 12-byte RTPS GUID prefix plus the 4-byte entity ID. */
   static final int GUID_SIZE = 16;
   /** Upper bound of the node name and namespace strings. */
   static final int STRING_BOUND = 256;

   private RmwDdsCommonMessages()
   {
   }

   private static void checkGidSize(int gidSize)
   {
      if (gidSize != GID_SIZE_IRON_PLUS && gidSize != GID_SIZE_HUMBLE)
      {
         throw new IllegalArgumentException("GID size must be " + GID_SIZE_IRON_PLUS + " or " + GID_SIZE_HUMBLE + ", got " + gidSize);
      }
   }

   /**
    * Writes zero bytes up to the next multiple of {@code alignment}, relative to the end of the encapsulation header.
    */
   private static void pad(CDRBuffer buffer, int alignment)
   {
      int padding = CDRBuffer.alignment(buffer.getBufferUnsafe().position() - CDRBuffer.PAYLOAD_HEADER.length, alignment);
      for (int i = 0; i < padding; ++i)
      {
         buffer.writeByte((byte) 0);
      }
   }

   private static void writeUInt32(CDRBuffer buffer, int value)
   {
      pad(buffer, 4);
      buffer.writeInt(value);
   }

   private static int readLength(CDRBuffer buffer, int elementSize, String what)
   {
      buffer.alignBuffer(4);
      ByteBuffer bytes = buffer.getBufferUnsafe();
      if (bytes.remaining() < 4)
      {
         throw new IllegalArgumentException("Not enough data for the length of " + what);
      }
      int length = bytes.getInt();
      if (length < 0 || (long) length * elementSize > bytes.remaining())
      {
         throw new IllegalArgumentException("Invalid length " + Integer.toUnsignedString(length) + " for " + what + " (" + bytes.remaining() + " bytes remaining)");
      }
      return length;
   }

   private static int stringSizeBytes(int currentAlignment, String value)
   {
      int size = 4 + CDRBuffer.alignment(currentAlignment, 4);
      return size + value.getBytes(StandardCharsets.UTF_8).length + 1;
   }

   private static void writeString(CDRBuffer buffer, String value)
   {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      if (bytes.length > STRING_BOUND)
      {
         throw new IllegalArgumentException("String exceeds the bound of " + STRING_BOUND + " bytes: " + value);
      }
      writeUInt32(buffer, bytes.length + 1);
      for (byte b : bytes)
      {
         buffer.writeByte(b);
      }
      buffer.writeByte((byte) 0);
   }

   private static String readString(CDRBuffer buffer, String what)
   {
      int length = readLength(buffer, 1, what);
      if (length == 0)
      {
         return "";
      }
      byte[] bytes = new byte[length];
      buffer.getBufferUnsafe().get(bytes);
      int end = length;
      while (end > 0 && bytes[end - 1] == 0)
      {
         end--;
      }
      return new String(bytes, 0, end, StandardCharsets.UTF_8);
   }

   /**
    * {@code rmw_dds_common/msg/Gid}: an RTPS GUID (prefix + entity ID) zero-extended to the GID size.
    */
   static final class Gid implements ROS2Message<Gid>
   {
      public static final java.lang.String name = "rmw_dds_common::msg::dds_::Gid_";

      private final int gidSize;
      private final byte[] data;

      Gid()
      {
         this(GID_SIZE_IRON_PLUS);
      }

      Gid(int gidSize)
      {
         checkGidSize(gidSize);
         this.gidSize = gidSize;
         data = new byte[gidSize];
      }

      /**
       * @param gidSize 16 or 24
       * @param guid    a 16-byte RTPS GUID (prefix + entity ID)
       */
      Gid(int gidSize, byte[] guid)
      {
         this(gidSize);
         setGuid(guid);
      }

      void setGuid(byte[] guid)
      {
         if (guid.length != GUID_SIZE)
         {
            throw new IllegalArgumentException("A GUID has " + GUID_SIZE + " bytes, got " + guid.length);
         }
         Arrays.fill(data, (byte) 0);
         System.arraycopy(guid, 0, data, 0, GUID_SIZE);
      }

      /** @return a copy of the 16 significant GUID bytes */
      byte[] getGuid()
      {
         return Arrays.copyOf(data, GUID_SIZE);
      }

      int getGidSize()
      {
         return gidSize;
      }

      /** @return the GID storage (gidSize bytes); modifications change this GID */
      byte[] getData()
      {
         return data;
      }

      @Override
      public int calculateSizeBytes(int currentAlignment)
      {
         return gidSize;
      }

      @Override
      public void serialize(CDRBuffer buffer)
      {
         for (int i = 0; i < gidSize; ++i)
         {
            buffer.writeByte(data[i]);
         }
      }

      @Override
      public void deserialize(CDRBuffer buffer)
      {
         if (buffer.getBufferUnsafe().remaining() < gidSize)
         {
            throw new IllegalArgumentException("Not enough data for a " + gidSize + "-byte GID");
         }
         buffer.getBufferUnsafe().get(data);
      }

      @Override
      public void set(Gid from)
      {
         if (from.gidSize == gidSize)
         {
            System.arraycopy(from.data, 0, data, 0, gidSize);
         }
         else
         {
            setGuid(from.getGuid());
         }
      }

      @Override
      public boolean equals(Object obj)
      {
         return obj instanceof Gid && Arrays.equals(data, ((Gid) obj).data);
      }

      @Override
      public int hashCode()
      {
         return Arrays.hashCode(data);
      }

      @Override
      public java.lang.String toString()
      {
         StringBuilder builder = new StringBuilder();
         for (int i = 0; i < gidSize; ++i)
         {
            if (i > 0)
            {
               builder.append('.');
            }
            builder.append(Character.forDigit((data[i] >> 4) & 0xF, 16)).append(Character.forDigit(data[i] & 0xF, 16));
         }
         return builder.toString();
      }
   }

   /**
    * {@code rmw_dds_common/msg/NodeEntitiesInfo}: one node and the GIDs of its readers and writers.
    */
   static final class NodeEntitiesInfo implements ROS2Message<NodeEntitiesInfo>
   {
      public static final java.lang.String name = "rmw_dds_common::msg::dds_::NodeEntitiesInfo_";

      private final int gidSize;
      private java.lang.String nodeNamespace = "";
      private java.lang.String nodeName = "";
      private final List<Gid> readerGids = new ArrayList<>();
      private final List<Gid> writerGids = new ArrayList<>();

      NodeEntitiesInfo()
      {
         this(GID_SIZE_IRON_PLUS);
      }

      NodeEntitiesInfo(int gidSize)
      {
         checkGidSize(gidSize);
         this.gidSize = gidSize;
      }

      NodeEntitiesInfo(int gidSize, java.lang.String nodeNamespace, java.lang.String nodeName, List<byte[]> readerGuids, List<byte[]> writerGuids)
      {
         this(gidSize);
         setNodeNamespace(nodeNamespace);
         setNodeName(nodeName);
         for (byte[] guid : readerGuids)
         {
            readerGids.add(new Gid(gidSize, guid));
         }
         for (byte[] guid : writerGuids)
         {
            writerGids.add(new Gid(gidSize, guid));
         }
      }

      java.lang.String getNodeNamespace()
      {
         return nodeNamespace;
      }

      void setNodeNamespace(java.lang.String nodeNamespace)
      {
         this.nodeNamespace = nodeNamespace;
      }

      java.lang.String getNodeName()
      {
         return nodeName;
      }

      void setNodeName(java.lang.String nodeName)
      {
         this.nodeName = nodeName;
      }

      List<Gid> getReaderGids()
      {
         return readerGids;
      }

      List<Gid> getWriterGids()
      {
         return writerGids;
      }

      @Override
      public int calculateSizeBytes(int currentAlignment)
      {
         int initialAlignment = currentAlignment;
         currentAlignment += stringSizeBytes(currentAlignment, nodeNamespace);
         currentAlignment += stringSizeBytes(currentAlignment, nodeName);
         currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4) + readerGids.size() * gidSize;
         currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4) + writerGids.size() * gidSize;
         return currentAlignment - initialAlignment;
      }

      @Override
      public void serialize(CDRBuffer buffer)
      {
         writeString(buffer, nodeNamespace);
         writeString(buffer, nodeName);
         writeUInt32(buffer, readerGids.size());
         for (Gid gid : readerGids)
         {
            checkElement(gid);
            gid.serialize(buffer);
         }
         writeUInt32(buffer, writerGids.size());
         for (Gid gid : writerGids)
         {
            checkElement(gid);
            gid.serialize(buffer);
         }
      }

      private void checkElement(Gid gid)
      {
         if (gid.getGidSize() != gidSize)
         {
            throw new IllegalStateException("GID size " + gid.getGidSize() + " does not match the message GID size " + gidSize);
         }
      }

      @Override
      public void deserialize(CDRBuffer buffer)
      {
         nodeNamespace = readString(buffer, "node_namespace");
         nodeName = readString(buffer, "node_name");
         readGids(buffer, readerGids, "reader_gid_seq");
         readGids(buffer, writerGids, "writer_gid_seq");
      }

      private void readGids(CDRBuffer buffer, List<Gid> gids, java.lang.String what)
      {
         int count = readLength(buffer, gidSize, what);
         gids.clear();
         for (int i = 0; i < count; ++i)
         {
            Gid gid = new Gid(gidSize);
            gid.deserialize(buffer);
            gids.add(gid);
         }
      }

      @Override
      public void set(NodeEntitiesInfo from)
      {
         nodeNamespace = from.nodeNamespace;
         nodeName = from.nodeName;
         copyGids(from.readerGids, readerGids);
         copyGids(from.writerGids, writerGids);
      }

      private void copyGids(List<Gid> from, List<Gid> to)
      {
         to.clear();
         for (Gid gid : from)
         {
            Gid copy = new Gid(gidSize);
            copy.set(gid);
            to.add(copy);
         }
      }

      @Override
      public java.lang.String toString()
      {
         return "NodeEntitiesInfo {node_namespace=" + nodeNamespace + ", node_name=" + nodeName + ", reader_gid_seq=" + readerGids + ", writer_gid_seq="
                + writerGids + "}";
      }
   }

   /**
    * {@code rmw_dds_common/msg/ParticipantEntitiesInfo}: the participant GID and every node of the participant.
    * Receivers replace their whole view of the participant with each sample, so a sample must always list all nodes.
    */
   static final class ParticipantEntitiesInfo implements ROS2Message<ParticipantEntitiesInfo>
   {
      public static final java.lang.String name = "rmw_dds_common::msg::dds_::ParticipantEntitiesInfo_";
      /** RIHS01 type hash of ParticipantEntitiesInfo on Iron and newer. */
      public static final java.lang.String typeHash = "RIHS01_91a0593bacdcc50ea9bdcf849a938b128412cc1ea821245c663bcd26f83c295e";

      private final int gidSize;
      private final Gid gid;
      private final List<NodeEntitiesInfo> nodeEntitiesInfoSeq = new ArrayList<>();

      /** Required by {@link ROS2Message#createInstance(Class)}; uses 16-byte GIDs. */
      public ParticipantEntitiesInfo()
      {
         this(GID_SIZE_IRON_PLUS);
      }

      ParticipantEntitiesInfo(int gidSize)
      {
         checkGidSize(gidSize);
         this.gidSize = gidSize;
         gid = new Gid(gidSize);
      }

      int getGidSize()
      {
         return gidSize;
      }

      Gid getGid()
      {
         return gid;
      }

      List<NodeEntitiesInfo> getNodeEntitiesInfoSeq()
      {
         return nodeEntitiesInfoSeq;
      }

      @Override
      public int calculateSizeBytes(int currentAlignment)
      {
         int initialAlignment = currentAlignment;
         currentAlignment += gidSize;
         currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4);
         for (NodeEntitiesInfo node : nodeEntitiesInfoSeq)
         {
            currentAlignment += node.calculateSizeBytes(currentAlignment);
         }
         return currentAlignment - initialAlignment;
      }

      @Override
      public void serialize(CDRBuffer buffer)
      {
         gid.serialize(buffer);
         writeUInt32(buffer, nodeEntitiesInfoSeq.size());
         for (NodeEntitiesInfo node : nodeEntitiesInfoSeq)
         {
            if (node.gidSize != gidSize)
            {
               throw new IllegalStateException("Node GID size " + node.gidSize + " does not match the message GID size " + gidSize);
            }
            node.serialize(buffer);
         }
      }

      @Override
      public void deserialize(CDRBuffer buffer)
      {
         gid.deserialize(buffer);
         // Each node takes at least 4 + 1 + pad + 4 + 1 + pad + 4 + 4 = 24 bytes on the wire
         int count = readLength(buffer, 16, "node_entities_info_seq");
         nodeEntitiesInfoSeq.clear();
         for (int i = 0; i < count; ++i)
         {
            NodeEntitiesInfo node = new NodeEntitiesInfo(gidSize);
            node.deserialize(buffer);
            nodeEntitiesInfoSeq.add(node);
         }
      }

      @Override
      public void set(ParticipantEntitiesInfo from)
      {
         gid.set(from.gid);
         nodeEntitiesInfoSeq.clear();
         for (NodeEntitiesInfo node : from.nodeEntitiesInfoSeq)
         {
            NodeEntitiesInfo copy = new NodeEntitiesInfo(gidSize);
            copy.set(node);
            nodeEntitiesInfoSeq.add(copy);
         }
      }

      /**
       * Serializes this message, including the 4-byte encapsulation header, into a new byte array.
       */
      byte[] toBytes()
      {
         CDRBuffer buffer = new CDRBuffer();
         int size = CDRBuffer.PAYLOAD_HEADER.length + calculateSizeBytes(0);
         buffer.ensureRemainingCapacity(size);
         buffer.rewind();
         buffer.writePayloadHeader();
         serialize(buffer);
         int length = buffer.getBufferUnsafe().position();
         byte[] bytes = new byte[length];
         buffer.getBufferUnsafe().flip();
         buffer.getBufferUnsafe().get(bytes);
         return bytes;
      }

      /**
       * Parses a serialized sample (with its encapsulation header).
       *
       * @throws IllegalArgumentException if the data is malformed
       */
      static ParticipantEntitiesInfo fromBytes(byte[] bytes, int gidSize)
      {
         CDRBuffer buffer = new CDRBuffer();
         buffer.ensureRemainingCapacity(bytes.length);
         buffer.rewind();
         buffer.getBufferUnsafe().put(bytes);
         buffer.getBufferUnsafe().flip();
         if (bytes.length < CDRBuffer.PAYLOAD_HEADER.length)
         {
            throw new IllegalArgumentException("Missing encapsulation header");
         }
         buffer.readPayloadHeader();
         ParticipantEntitiesInfo message = new ParticipantEntitiesInfo(gidSize);
         try
         {
            message.deserialize(buffer);
         }
         catch (java.nio.BufferUnderflowException e)
         {
            throw new IllegalArgumentException("Truncated ParticipantEntitiesInfo: " + e);
         }
         return message;
      }

      @Override
      public java.lang.String toString()
      {
         return "ParticipantEntitiesInfo {gid=" + gid + ", node_entities_info_seq=" + nodeEntitiesInfoSeq + "}";
      }
   }
}
