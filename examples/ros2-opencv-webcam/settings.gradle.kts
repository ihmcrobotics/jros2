pluginManagement {
   repositories {
      gradlePluginPortal()
      maven { url = uri("https://robotlabfiles.ihmc.us/repository") }
   }
}

rootProject.name = "ros2-opencv-webcam"

includeBuild("../..") {
   dependencySubstitution {
      substitute(module("us.ihmc:jros2")).using(project(":"))
   }
}
