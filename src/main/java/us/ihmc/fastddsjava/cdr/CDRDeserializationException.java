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
package us.ihmc.fastddsjava.cdr;

/**
 * Thrown while deserializing a CDR payload that is malformed: a length prefix that is negative or larger than the bytes
 * left in the payload, a sequence that is longer than its declared upper bound, a payload that ends in the middle of a
 * value, an invalid boolean or wide character value, or an unsupported encapsulation.
 * <p>
 * Every wire length is validated against the remaining payload <em>before</em> anything is allocated, so a crafted
 * sample cannot force large allocations. Receivers should drop the sample when this exception is thrown; the
 * destination message may have been partially overwritten.
 * </p>
 */
public class CDRDeserializationException extends RuntimeException
{
   private static final long serialVersionUID = 1L;

   /**
    * @param message a description of what was malformed
    */
   public CDRDeserializationException(String message)
   {
      super(message);
   }

   /**
    * @param message a description of what was malformed
    * @param cause   the underlying exception
    */
   public CDRDeserializationException(String message, Throwable cause)
   {
      super(message, cause);
   }
}
