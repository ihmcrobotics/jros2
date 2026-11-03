pluginManagement {
   // Resolve the us.ihmc.jros2.generator plugin from this checkout (remove to use the published plugin version).
   includeBuild("../..")
   repositories {
      gradlePluginPortal()
      mavenCentral()
      maven { url = uri("https://robotlabfiles.ihmc.us/repository/") }
   }
}

rootProject.name = "custom-message-class"

includeBuild("../..") {
   dependencySubstitution {
      substitute(module("us.ihmc:jros2")).using(project(":"))
   }
}
