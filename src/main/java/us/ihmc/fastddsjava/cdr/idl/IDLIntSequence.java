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

import java.nio.IntBuffer;

public class IDLIntSequence extends IDLSequence<IDLIntSequence>
{
   private IntBuffer buffer;

   public IDLIntSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);
   }

   public IDLIntSequence(int maxSize)
   {
      super(maxSize);
   }

   public IDLIntSequence()
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

   public void add(int element)
   {
      if (buffer == null)
      {
         ensureMinCapacity(Math.min(getMaxSize(), DEFAULT_INITIAL_CAPACITY));
      }
      else if (!isUnbounded() && (buffer.position() >= getMaxSize()))
      {
         throw new RuntimeException("Cannot add element to the sequence, reached upper bound");
      }
      else if (buffer.position() == buffer.capacity())
      {
         ensureMinCapacity(2 * buffer.capacity());
      }

      buffer.put(element);
   }

   public int get(int index)
   {
      assert index < elements();

      return buffer.get(index);
   }

   public IntBuffer getBufferUnsafe()
   {
      return buffer;
   }

   @Override
   protected void ensureMinCapacity(int desiredCapacity)
   {
      if (capacity() < desiredCapacity)
      {
         IntBuffer newBuffer = IntBuffer.allocate(desiredCapacity);

         int currentElements = elements();
         if (currentElements != 0)
         {
            newBuffer.put(0, buffer, 0, currentElements);
            newBuffer.position(currentElements);
         }

         buffer = newBuffer;
      }
   }

   @Override
   public int elementSizeBytes(int i)
   {
      return 4;
   }

   @Override
   public void readElement(CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      buffer.put(cdrBuffer.readInt());
   }

   @Override
   public void writeElement(int i, CDRBuffer cdrBuffer)
   {
      assert buffer != null;

      cdrBuffer.writeInt(buffer.get(i));
   }

   @Override
   public void set(IDLIntSequence other)
   {
      clear();

      int othersElements = other.elements();
      ensureMinCapacity(othersElements);

      buffer.put(0, other.buffer, 0, othersElements);
      buffer.position(othersElements);
   }
}
