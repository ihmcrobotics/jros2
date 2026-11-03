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

/**
 * IDL sequence of wide strings. Each element is written as a {@code uint32} count of UTF-16 code units followed by one
 * {@code uint32} per code unit (the rmw_fastrtps format), see {@link CDRBuffer#writeWString(StringBuilder)}.
 */
public class IDLWStringSequence extends IDLStringSequence
{
   public IDLWStringSequence()
   {
      super();
   }

   public IDLWStringSequence(int maxSize)
   {
      super(maxSize);
   }

   public IDLWStringSequence(int capacity, int maxSize)
   {
      super(capacity, maxSize);
   }

   public IDLWStringSequence(int capacity, int maxSize, int defaultStringLength)
   {
      super(capacity, maxSize, defaultStringLength);
   }

   /**
    * A wide string is a {@code uint32} count of UTF-16 code units followed by one {@code uint32} per code unit.
    */
   @Override
   public int elementSizeBytes(int currentAlignment, int i)
   {
      return 4 + 4 * elements[i].length();
   }

   @Override
   public int calculateSizeBytes(int currentAlignment)
   {
      int initialAlignment = currentAlignment;

      currentAlignment += 4 + CDRBuffer.alignment(currentAlignment, 4); // sequence length prefix

      for (int i = 0; i < size(); i++)
      {
         currentAlignment += CDRBuffer.alignment(currentAlignment, 4);
         currentAlignment += 4 + 4 * elements[i].length(); // wstring length int + one uint32 per UTF-16 code unit
      }

      return currentAlignment - initialAlignment;
   }

   @Override
   public void readElement(CDRBuffer buffer)
   {
      StringBuilder element = elementAtCurrentPosition();
      buffer.readWString(element);
      position++;
   }

   @Override
   public void writeElement(int i, CDRBuffer buffer)
   {
      buffer.writeWString(elements[i]);
   }
}
