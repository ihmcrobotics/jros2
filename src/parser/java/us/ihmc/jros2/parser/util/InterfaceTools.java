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

      String[] package0Resource1 = new String[2];
      package0Resource1[0] = packageResourceName.split("/")[0];
      package0Resource1[1] = packageResourceName.split("/")[1];

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

   public static String parseJavaClassNameFromResourceName(String resourceName)
   {
      StringBuilder javaClassNameBuilder = new StringBuilder();
      for (int i = 0; i < resourceName.length(); i++)
      {
         char c = resourceName.charAt(i);

         if (javaClassNameBuilder.isEmpty())
         {
            if (Character.isJavaIdentifierStart(c))
            {
               javaClassNameBuilder.append(c);
            }
         }
         else
         {
            if (Character.isJavaIdentifierPart(c))
            {
               javaClassNameBuilder.append(c);
            }
         }
      }

      return javaClassNameBuilder.toString();
   }
}
