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
package us.ihmc.jros2.parser.util;

import java.util.Objects;

public final class InterfaceTools
{
   public static String[] checkAndParsePackageResourceName(String packageResourceName) throws NullPointerException, IllegalArgumentException
   {
      Objects.requireNonNull(packageResourceName);

      if (packageResourceName.isEmpty())
      {
         throw new IllegalArgumentException("packageResourceName cannot be empty");
      }

      if (!packageResourceName.contains("/"))
      {
         throw new IllegalArgumentException("packageResourceName must contain a package and a resource name delimited with a forward slash (/)");
      }

      String[] package0Resource1 = packageResourceName.split("/");

      if (package0Resource1.length != 2)
      {
         throw new IllegalArgumentException("More than one forward slash (/) found in packageResourceName");
      }

      if (package0Resource1[0].isEmpty())
      {
         throw new IllegalArgumentException("package name parsed from packageResourceName cannot be empty");
      }

      if (package0Resource1[1].isEmpty())
      {
         throw new IllegalArgumentException("resource name parsed from packageResourceName cannot be empty");
      }

      return package0Resource1;
   }

   public static void checkSchema(String schema) throws NullPointerException, IllegalArgumentException
   {
      Objects.requireNonNull(schema);

      if (schema.isEmpty())
      {
         throw new IllegalArgumentException("schema cannot be empty");
      }
   }
}
