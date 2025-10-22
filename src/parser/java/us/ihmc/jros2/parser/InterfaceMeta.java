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