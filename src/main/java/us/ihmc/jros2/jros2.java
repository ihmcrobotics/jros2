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
package us.ihmc.jros2;

import us.ihmc.fastddsjava.library.fastddsjavaNativeLibrary;
import us.ihmc.fastddsjava.profiles.ProfilesXML;

import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

final class jros2 implements jros2Settings
{
   private static final String SOURCE_NAME = "jros2.java";

   /**
    * Singleton instance of jros2.
    */
   private static jros2 instance;
   /**
    * Logger for jros2.
    */
   private static final Logger LOGGER = Logger.getLogger("jros2");

   static
   {
      LOGGER.setUseParentHandlers(false);
      StreamHandler handler = new StreamHandler(System.out, new Formatter()
      {
         private final java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

         @Override
         public String format(LogRecord record)
         {
            String timestamp = dateFormat.format(new java.util.Date(record.getMillis()));
            String method = record.getSourceClassName() + "." + record.getSourceMethodName();
            return String.format("%s [%s] %s: %s%n", timestamp, method, record.getLevel(), record.getMessage());
         }
      })
      {
         @Override
         public synchronized void publish(LogRecord record)
         {
            super.publish(record);
            flush();
         }
      };
      handler.setLevel(Level.ALL);
      LOGGER.addHandler(handler);
   }

   /**
    * Array of settings sources to query for setting values, in order of priority:
    * <ol>
    *    <li>Java system properties</li>
    *    <li>Environment variables</li>
    *    <li><code>jros2.properties</code> file</li>
    *    <li>Default settings</li>
    * </ol>
    */
   private final jros2Settings[] settingsSources;
   private final boolean loaded;

   private jros2()
   {
      this(new jros2Settings[] {new jros2SettingsProp(), new jros2SettingsEnv(), new jros2SettingsFile(), new jros2SettingsDefault()});

      /*
       * Intraprocess delivery mode can only be set once per jros2 instance, not per-node. Consider the following scenario:
       *
       *   1. Create node A with intraprocess OFF
       *   2. Create node B with intraprocess ON
       *   3. Create publisher using node A
       *
       *   You may expect the publisher to not use intraprocess, but it's most likely that it will because node B has enabled intraprocess for the entire
       *   Fast-DDS library instance.
       *
       * See: https://fast-dds.docs.eprosima.com/en/v3.2.2/fastdds/xml_configuration/library_settings.html#intra-process-delivery-xml-profile
       * Notice how intraprocess delivery is a library setting, not a participant, data reader, or data writer setting.
       */
      ProfilesXML.setIntraprocessDelivery(intraprocessDelivery() ? "FULL" : "OFF");
   }

   /**
    * Constructor for unit tests. Do not use in source code.
    * <p>
    * Use {@link #load()} to create a singleton instance and {@link #get()} to access the instance.
    *
    * @param settingsSources Setting sources to use.
    */
   jros2(jros2Settings[] settingsSources)
   {
      this.settingsSources = settingsSources;

      boolean android = System.getProperty("java.vendor").toLowerCase().contains("android");

      boolean loaded = false;

      /*
       * Attempt to load native libraries for Android if the current platform is Android.
       * Otherwise, attempt to load the native libraries using the fastddsjavaNativeLibrary (ihmc-native-library-loader) class.
       */
      if (android)
      {
         try
         {
            System.loadLibrary("log"); // Android logging library
            System.loadLibrary("c++_shared"); // C++ STL
            System.loadLibrary("fastcdr");
            System.loadLibrary("fastdds");
            System.loadLibrary("jnifastddsjava");

            loaded = true;
         }
         catch (UnsatisfiedLinkError ignored)
         {
         }
      }
      else
      {
         loaded = fastddsjavaNativeLibrary.load();
      }

      this.loaded = loaded;

      instance = this;
   }

   static synchronized void load()
   {
      if (instance == null)
      {
         instance = new jros2();
      }
   }

   static jros2 get()
   {
      if (instance == null)
      {
         throw new RuntimeException("jros2 not initialized");
      }

      return instance;
   }

   /**
    * Get the logger for jros2.
    *
    * @return The logger for jros2.
    */
   public static Logger getLogger()
   {
      return LOGGER;
   }

   /**
    * Log an exception with a message at SEVERE level.
    *
    * @param throwable The exception to log
    */
   public static void logError(Throwable throwable)
   {
      getLogger().log(Level.SEVERE, throwable.getMessage(), throwable);
   }

   /**
    * Log an exception with a custom message at SEVERE level.
    *
    * @param message   Custom message
    * @param throwable The exception to log
    */
   public static void logError(String message, Throwable throwable)
   {
      getLogger().log(Level.SEVERE, message, throwable);
   }

   boolean isLoaded()
   {
      return loaded;
   }

   @Override
   public String getSourceName()
   {
      return SOURCE_NAME;
   }

   @Override
   public int rosDomainId()
   {
      // Loop through setting sources in order of priority
      for (int i = 0; i < settingsSources.length; ++i)
      {
         // If the source specifies a default domain id, return the value
         if (settingsSources[i].hasROSDomainId())
         {
            return settingsSources[i].rosDomainId();
         }
      }

      // Realistically should never reach here
      return settingsSources[settingsSources.length - 1].rosDomainId();
   }

   @Override
   public boolean hasROSDomainId()
   {
      for (int i = 0; i < settingsSources.length; ++i)
      {
         if (settingsSources[i].hasROSDomainId())
         {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean intraprocessDelivery()
   {
      // Loop through setting sources in order of priority
      for (int i = 0; i < settingsSources.length; ++i)
      {
         // If the source specifies intraprocess delivery, return the value
         if (settingsSources[i].hasIntraprocessDelivery())
         {
            return settingsSources[i].intraprocessDelivery();
         }
      }

      // Realistically should never reach here
      return settingsSources[settingsSources.length - 1].intraprocessDelivery();
   }

   @Override
   public boolean hasIntraprocessDelivery()
   {
      for (int i = 0; i < settingsSources.length; ++i)
      {
         if (settingsSources[i].hasIntraprocessDelivery())
         {
            return true;
         }
      }

      return false;
   }

   @Override
   public String[] interfaceWhitelist()
   {
      // Loop through setting sources in order of priority
      for (int i = 0; i < settingsSources.length; ++i)
      {
         // If the source specifies a default domain id, return the value
         if (settingsSources[i].hasInterfaceWhitelist())
         {
            return settingsSources[i].interfaceWhitelist();
         }
      }

      // Realistically should never reach here
      return settingsSources[settingsSources.length - 1].interfaceWhitelist();
   }

   @Override
   public boolean hasInterfaceWhitelist()
   {
      for (int i = 0; i < settingsSources.length; ++i)
      {
         if (settingsSources[i].hasInterfaceWhitelist())
         {
            return true;
         }
      }

      return false;
   }

   public jros2Settings[] getSettingsSources()
   {
      return settingsSources;
   }
}
