pluginManagement {
   repositories {
      gradlePluginPortal()
      maven { url = uri("https://robotlabfiles.ihmc.us/repository") }
   }
}

rootProject.name = "native-check"

includeBuild("../..") {
   dependencySubstitution {
      substitute(module("us.ihmc:jros2")).using(project(":"))
   }
}
