pluginManagement {
   plugins {
      id("us.ihmc.ihmc-build") version "1.3.0"
   }
   repositories {
      mavenCentral()
      maven { url = uri("https://robotlabfiles.ihmc.us/repository") }
   }
}

buildscript {
   repositories {
      maven { url = uri("https://plugins.gradle.org/m2/") }
      maven { url = uri("https://robotlabfiles.ihmc.us/repository") }
   }
   dependencies {
      classpath("us.ihmc:ihmc-build:1.3.0")
   }
}

val ihmcSettingsConfigurator = us.ihmc.build.IHMCSettingsConfigurator(settings, logger, extra)
ihmcSettingsConfigurator.checkRequiredPropertiesAreSet()
ihmcSettingsConfigurator.configureExtraSourceSets()
ihmcSettingsConfigurator.findAndIncludeCompositeBuilds()
