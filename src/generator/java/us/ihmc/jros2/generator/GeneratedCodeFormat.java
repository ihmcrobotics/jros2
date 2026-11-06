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
package us.ihmc.jros2.generator;

import java.util.Arrays;
import java.util.Locale;

/**
 * Source code format of generated message classes.
 */
public enum GeneratedCodeFormat
{
   /** IHMC style: three-space indentation with opening braces on their own line. The default. */
   IHMC,
   /** Google Java Style, applied by google-java-format. See {@link GoogleJavaFormatRunner}. */
   GOOGLE;

   /**
    * @param name a format name, in any case
    * @return the format with that name
    * @throws IllegalArgumentException if there is no such format
    */
   public static GeneratedCodeFormat parse(String name)
   {
      try
      {
         return valueOf(name.trim().toUpperCase(Locale.ROOT));
      }
      catch (IllegalArgumentException | NullPointerException e)
      {
         throw new IllegalArgumentException("Unknown generated code format '" + name + "'. Use one of " + Arrays.toString(values()), e);
      }
   }
}
