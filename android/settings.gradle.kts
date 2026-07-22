pluginManagement {
   plugins {
      id("com.android.library") version "8.7.3"
   }
   repositories {
      google()
      mavenCentral()
      gradlePluginPortal()
   }
}

dependencyResolutionManagement {
   repositories {
      google()
      mavenCentral()
   }
}

rootProject.name = "jros2-android"
