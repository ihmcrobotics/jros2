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
package us.ihmc.fastddsjava.cdr.idl;

import us.ihmc.fastddsjava.cdr.CDRDeserializationException;

/**
 * The exceptions thrown by the IDL sequences, with their messages.
 * <p>
 * They are built here, in a class that only error paths use, so that the constant pools of the sequence classes hold no
 * string literal: HotSpot resolves (creates) every not yet resolved string literal of a class on the thread that first
 * requests a C2 compilation of one of its methods, which would allocate on a control thread in the middle of a steady
 * state.
 * </p>
 */
final class IDLSequenceErrors
{
   private IDLSequenceErrors()
   {
   }

   static IllegalArgumentException negativeMaxSize()
   {
      return new IllegalArgumentException("IDLSequence maxSize cannot be negative");
   }

   static IllegalArgumentException capacityAboveMaxSize()
   {
      return new IllegalArgumentException("IDLSequence capacity cannot be larger than maxSize");
   }

   static IllegalArgumentException exceedsUpperBound(long size, int maxSize)
   {
      return new IllegalArgumentException("array size exceeds upper bound: " + size + " > " + maxSize);
   }

   static IndexOutOfBoundsException negativeIndex(int index)
   {
      return new IndexOutOfBoundsException("Index: " + index);
   }

   static IndexOutOfBoundsException indexOutOfBounds(int index, int size)
   {
      return new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
   }

   static CDRDeserializationException unexpectedBoolean(byte value, int index)
   {
      return new CDRDeserializationException("Unexpected boolean value " + (value & 0xFF) + " at sequence index " + index + ", expected 0 or 1");
   }

   static RuntimeException cannotCreateElement(Class<?> elementClass, Exception cause)
   {
      return new RuntimeException("Unable to create an instance of CDRSerializable class: " + elementClass.getName(), cause);
   }

   static NullPointerException nullStringElement()
   {
      return new NullPointerException("IDLStringSequence.add(String) element is null");
   }
}
