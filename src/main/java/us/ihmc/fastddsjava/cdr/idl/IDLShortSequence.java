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

import java.nio.ShortBuffer;

public class IDLShortSequence extends IDLSequence<IDLShortSequence>
{
   private static final ShortBuffer EMPTY_BUFFER = ShortBuffer.allocate(0);

   private ShortBuffer buffer;

   public IDLShortSequence()
   {
      buffer = EMPTY_BUFFER;
   }

   public IDLShortSequence(int capacity)
   {
      this(capacity, IDLSequence.UNBOUNDED_MAX_SIZE);
   }

   public IDLShortSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);

      buffer = EMPTY_BUFFER;

      ensureMinCapacity(capacity);
   }

   /**
    * Get the backing heap {@link ShortBuffer} holding all short values in the sequence.
    * Use this for efficient copy operations, however ensure the buffer is the correct capacity
    * first with {@link #ensureMinCapacity(int)}!
    *
    * @return the buffer of short values
    */
   public ShortBuffer getBuffer()
   {
      return buffer;
   }

   @Override
   public int size()
   {
      return buffer.position();
   }

   @Override
   public int capacity()
   {
      return buffer.capacity();
   }

   @Override
   public void clear()
   {
      buffer.clear();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean ensureMinCapacity(int desiredCapacity)
   {
      if (buffer.capacity() < desiredCapacity)
      {
         desiredCapacity = Math.min(Math.max(desiredCapacity, buffer.capacity() * CAPACITY_GROW_SCALAR), getMaxSize());

         if (desiredCapacity > getMaxSize())
         {
            return false;
         }
         else
         {
            ShortBuffer newBuffer = ShortBuffer.allocate(desiredCapacity);
            newBuffer.put(0, buffer, 0, buffer.position());
            newBuffer.position(buffer.position());

            buffer = newBuffer;
         }
      }

      return true;
   }

   @Override
   public int elementSizeBytes(int currentAlignment, int i)
   {
      return 2 + CDRBuffer.alignment(currentAlignment, 2);
   }

   @Override
   public void readElement(CDRBuffer cdrBuffer)
   {
      buffer.put(cdrBuffer.readShort());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      cdrBuffer.writeShort(buffer.get(i));
   }

   @Override
   public void set(IDLShortSequence other)
   {
      clear();

      int othersElements = other.size();
      ensureMinCapacity(othersElements);

      buffer.put(0, other.buffer, 0, othersElements);
      buffer.position(othersElements);
   }
}
