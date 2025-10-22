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
package us.ihmc.jros2.parser;

import us.ihmc.jros2.parser.util.InterfaceTools;

public class InterfaceMeta
{
   private String packageResourceName;
   private String packageName;
   private String resourceName;
   private String headerComment;
   private String javaClassName;
   private String javaPackageName;

   public void setPackageResourceName(String packageResourceName)
   {
      this.packageResourceName = packageResourceName;

      String[] package0Resource1 = InterfaceTools.checkAndParsePackageResourceName(packageResourceName);
      packageName = package0Resource1[0];
      resourceName = package0Resource1[1];
      javaPackageName = packageName + ".msg.dds";
      javaClassName = resourceName; // TODO: Possibly add sanitation
   }

   public String getPackageResourceName()
   {
      return packageResourceName;
   }

   public String getPackageName()
   {
      return packageName;
   }

   public String getResourceName()
   {
      return resourceName;
   }

   public void setHeaderComment(String headerComment)
   {
      this.headerComment = headerComment;
   }

   public String getHeaderComment()
   {
      return headerComment;
   }

   public void setJavaClassName(String javaClassName)
   {
      this.javaClassName = javaClassName;
   }

   public String getJavaClassName()
   {
      return javaClassName;
   }

   public String getJavaPackageName()
   {
      return javaPackageName;
   }

   @Override
   public String toString()
   {
      return "InterfaceMeta{" + "packageResourceName='" + packageResourceName + '\'' + ", packageName='" + packageName + '\'' + ", resourceName='"
             + resourceName + '\'' + ", headerComment='" + headerComment + '\'' + ", javaClassName='" + javaClassName + '\'' + ", javaPackageName='"
             + javaPackageName + '\'' + '}';
   }
}