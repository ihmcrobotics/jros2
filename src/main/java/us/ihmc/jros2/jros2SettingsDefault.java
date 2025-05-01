package us.ihmc.jros2;

class jros2SettingsDefault implements jros2Settings
{
   @Override
   public int rosDomainId()
   {
      return 0;
   }

   @Override
   public boolean hasROSDomainId()
   {
      return true;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      return new String[0];
   }

   @Override
   public boolean hasInterfaceWhitelist()
   {
      return true;
   }
}
