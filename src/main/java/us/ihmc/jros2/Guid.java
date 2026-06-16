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

import java.util.Arrays;

/**
 * A 16-byte DDS-RTPS GUID (Globally Unique Identifier): 12-byte participant prefix plus 4-byte entity ID.
 * <p>
 * {@link ROS2Publisher#getGuid()} and {@link ROS2Subscription#getGuid()} return a cached instance owned by that
 * endpoint. Its bytes are refreshed on each call. Copy with {@link #set(Guid)} or {@link #set(byte[])} if you
 * need an independent snapshot (for example in a matched callback or a map key).
 */
public class Guid
{
   private final byte[] guid;

   public Guid()
   {
      guid = new byte[16];
   }

   public Guid(byte[] guid)
   {
      this.guid = new byte[16];
      set(guid);
   }

   /**
    * Copies a 16-byte GUID into this instance.
    *
    * @param guid 16-byte array containing the GUID
    */
   public void set(byte[] guid)
   {
      if (guid.length != 16)
      {
         throw new IllegalArgumentException("GUID must be exactly 16 bytes, got " + guid.length);
      }
      System.arraycopy(guid, 0, this.guid, 0, 16);
   }

   /**
    * Copies another {@link Guid} into this instance.
    *
    * @param other GUID to copy from
    */
   public void set(Guid other)
   {
      System.arraycopy(other.guid, 0, this.guid, 0, 16);
   }

   /**
    * Returns the internal 16-byte array. Modifications to the returned array update this GUID.
    *
    * @return 16-byte GUID storage
    */
   public byte[] getValue()
   {
      return guid;
   }

   @Override
   public boolean equals(Object obj)
   {
      if (this == obj)
         return true;
      if (obj == null || getClass() != obj.getClass())
         return false;
      Guid other = (Guid) obj;
      return Arrays.equals(guid, other.guid);
   }

   @Override
   public int hashCode()
   {
      return Arrays.hashCode(guid);
   }

   @Override
   public String toString()
   {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < guid.length; i++)
      {
         if (i == 12)
            sb.append('.');
         sb.append(String.format("%02x", guid[i]));
      }
      return sb.toString();
   }
}
