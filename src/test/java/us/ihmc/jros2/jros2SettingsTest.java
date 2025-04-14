package us.ihmc.jros2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class jros2SettingsTest
{
   @Test
   public void testSettings()
   {
      jros2Settings settings = new jros2Settings();

      settings.load();

      Assertions.assertEquals(0, settings.getDefaultDomainId());
   }
}
