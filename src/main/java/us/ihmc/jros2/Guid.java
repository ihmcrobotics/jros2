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
 * Represents a DDS-RTPS GUID (Globally Unique Identifier).
 * A GUID is a 16-byte identifier composed of a 12-byte prefix and a 4-byte entity ID.
 *
 * Based on the ihmc-ros2-library Guid class.
 * <p>
 * Reusable buffer: {@link ROS2Publisher#getGuid()} and {@link ROS2Subscription#getGuid()} return a cached
 * instance; use {@link #set(Guid)} to copy into your own storage when a snapshot is needed.
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
    * Set the GUID from a 16-byte array.
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
    * Set the GUID from another Guid instance.
    * @param other Guid to copy from
    */
   public void set(Guid other)
   {
      System.arraycopy(other.guid, 0, this.guid, 0, 16);
   }

   /**
    * Get direct access to the internal GUID byte array.
    * WARNING: Modifications to this array will affect the GUID!
    * @return 16-byte array containing the GUID
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
