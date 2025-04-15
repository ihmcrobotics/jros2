package us.ihmc.jros2;

public class jros2SettingsDefault implements jros2Settings
{
   @Override
   public int defaultDomainId()
   {
      return 0;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      return new String[0];
   }
}
