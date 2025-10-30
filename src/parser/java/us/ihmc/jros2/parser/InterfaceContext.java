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

import us.ihmc.jros2.parser.field.InterfaceField;
import us.ihmc.jros2.parser.util.InterfaceTools;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Representation of a ROS 2 interface. Possible implementations:
 * <ul>
 *   <li>ROS 2 msg (.msg)</li>
 *   <li>ROS 2 service (.srv)</li>
 *   <li>ROS 2 action (.action)</li>
 * </ul>
 */
public abstract class InterfaceContext
{
   private final String schema;
   private final Map<String, InterfaceField> fields;

   private String packageResourceName;
   private String packageName;
   private String resourceName;
   private String headerComment;
   private String javaClassName;
   private String javaPackageName;

   public InterfaceContext(String schema)
   {
      this.schema = schema;
      fields = new LinkedHashMap<>();
   }

   public String getSchema()
   {
      return schema;
   }

   public Map<String, InterfaceField> getFields()
   {
      return fields;
   }

   public List<InterfaceField> getFieldList()
   {
      return new LinkedList<>(fields.values());
   }

   public void setPackageResourceName(String packageResourceName)
   {
      this.packageResourceName = packageResourceName;

      String[] package0Resource1 = InterfaceTools.checkAndParsePackageResourceName(packageResourceName);
      packageName = package0Resource1[0];
      resourceName = package0Resource1[1];
      javaPackageName = packageName + ".msg.dds";
      javaClassName = resourceName.split("\\.")[0]; // TODO: Possibly add sanitation
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
