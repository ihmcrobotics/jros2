/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
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
package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRBuffer;
import us.ihmc.log.LogTools;

import java.nio.DoubleBuffer;

public class IDLDoubleSequence extends IDLSequence<IDLDoubleSequence>
{
   private DoubleBuffer buffer;

   public IDLDoubleSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);
   }

   public IDLDoubleSequence(int capacity)
   {
      super(capacity, IDLSequence.UNBOUNDED_MAX_SIZE);
   }

   public IDLDoubleSequence()
   {

   }

   @Override
   public int elements()
   {
      if (buffer == null)
      {
         return 0;
      }

      return buffer.position();
   }

   @Override
   public int capacity()
   {
      if (buffer == null)
      {
         return 0;
      }

      return buffer.capacity();
   }

   @Override
   public void clear()
   {
      if (buffer != null)
      {
         buffer.clear();
      }
   }

   /**
    * Get the backing heap {@link DoubleBuffer} holding all double values in the sequence.
    * Use this for efficient copy operations, however ensure the buffer is initialized and
    * of the correct capacity first with {@link #ensureMinCapacity(int)}!
    *
    * @return the buffer of double values, may be null
    */
   public DoubleBuffer getBuffer()
   {
      return buffer;
   }

   @Override
   public void ensureMinCapacity(int desiredCapacity)
   {
      if (capacity() < desiredCapacity)
      {
         if (desiredCapacity > getMaxSize())
         {
            LogTools.error("Cannot add element to the sequence, reached upper bound");

            return;
         }

         if (buffer != null)
         {
            desiredCapacity = Math.min(Math.max(desiredCapacity, buffer.capacity() * 2), getMaxSize());
         }

         DoubleBuffer newBuffer = DoubleBuffer.allocate(desiredCapacity);

         if (buffer != null)
         {
            newBuffer.put(0, buffer, 0, buffer.position());
            newBuffer.position(buffer.position());
         }

         buffer = newBuffer;
      }
   }

   @Override
   public int elementSizeBytes(int currentAlignment, int i)
   {
      return 8 + CDRBuffer.alignment(currentAlignment, 8);
   }

   @Override
   public void readElement(CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      buffer.put(cdrBuffer.readDouble());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      cdrBuffer.writeDouble(buffer.get(i));
   }

   @Override
   public void set(IDLDoubleSequence other)
   {
      clear();

      int othersElements = other.elements();
      ensureMinCapacity(othersElements);

      buffer.put(0, other.buffer, 0, othersElements);
      buffer.position(othersElements);
   }
}
