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

import java.util.Objects;

/**
 * Identity of a DDS sample: the GUID of the writer that wrote it plus the sequence number assigned by that writer.
 * Fast-DDS uses it to correlate ROS 2 service requests and responses: a response is written with its
 * {@code related_sample_identity} set to the {@code sample_identity} of the request.
 * <p>
 * Instances are mutable so they can be reused without allocating.
 */
public class SampleIdentity
{
   /** Sequence number of an unknown sample identity in Fast-DDS ({@code SequenceNumber_t::unknown()}, high = -1, low = 0). */
   public static final long UNKNOWN_SEQUENCE_NUMBER = 0xFFFFFFFF00000000L;

   private final Guid writerGuid;
   private long sequenceNumber;

   public SampleIdentity()
   {
      writerGuid = new Guid();
      sequenceNumber = UNKNOWN_SEQUENCE_NUMBER;
   }

   public SampleIdentity(Guid writerGuid, long sequenceNumber)
   {
      this();
      set(writerGuid, sequenceNumber);
   }

   public void set(Guid writerGuid, long sequenceNumber)
   {
      this.writerGuid.set(writerGuid);
      this.sequenceNumber = sequenceNumber;
   }

   public void set(SampleIdentity other)
   {
      set(other.writerGuid, other.sequenceNumber);
   }

   /**
    * @return the writer GUID. Modifications to the returned instance update this identity.
    */
   public Guid getWriterGuid()
   {
      return writerGuid;
   }

   public long getSequenceNumber()
   {
      return sequenceNumber;
   }

   public void setSequenceNumber(long sequenceNumber)
   {
      this.sequenceNumber = sequenceNumber;
   }

   /**
    * @return true if this is the unknown identity (zero GUID and unknown sequence number), as for samples written without one.
    */
   public boolean isUnknown()
   {
      if (sequenceNumber != UNKNOWN_SEQUENCE_NUMBER)
         return false;
      for (byte b : writerGuid.getValue())
      {
         if (b != 0)
            return false;
      }
      return true;
   }

   @Override
   public boolean equals(Object obj)
   {
      if (this == obj)
         return true;
      if (obj == null || getClass() != obj.getClass())
         return false;
      SampleIdentity other = (SampleIdentity) obj;
      return sequenceNumber == other.sequenceNumber && writerGuid.equals(other.writerGuid);
   }

   @Override
   public int hashCode()
   {
      return Objects.hash(writerGuid, sequenceNumber);
   }

   @Override
   public String toString()
   {
      return writerGuid + ":" + sequenceNumber;
   }
}
