package us.ihmc.jros2.interfaces.context;

public abstract class InterfaceContext
{
   private final String packageName;
   private final String name;

   public InterfaceContext(String packageName, String name)
   {
      this.packageName = packageName;
      this.name = name;
   }

   public String getPackageName()
   {
      return packageName;
   }

   public String getName()
   {
      return name;
   }

   public String getNameWithoutExtension()
   {
      return getName().substring(0, getName().indexOf("."));
   }

   public String getJavaClassName()
   {
      return getNameWithoutExtension();
   }
}
