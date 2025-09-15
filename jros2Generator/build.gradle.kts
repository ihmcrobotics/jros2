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
plugins {
   id("java")
   id("java-gradle-plugin")

   id("com.gradle.plugin-publish") version "1.3.1"
}

group = "us.ihmc"
version = "1.0.2"

gradlePlugin {
   website.set("https://github.com/ihmcrobotics/jros2")
   vcsUrl.set("https://github.com/ihmcrobotics/jros2")
   plugins {
      create("jros2Generator") {
         id = "us.ihmc.jros2.generator"
         displayName = "jros2 Interface Generator"
         description = "Gradle plugin to generate Java ROS 2 interface classes from .msg, .srv, .action files"
         tags.set(listOf("jros2", "ros2"))
         implementationClass = "us.ihmc.jros2.generator.jros2GeneratorPlugin"
      }
   }
}

java {
   sourceCompatibility = JavaVersion.VERSION_17
   targetCompatibility = JavaVersion.VERSION_17
}

repositories {
   mavenCentral()
}

/*
 * Copy all default ROS 2 packages into the jar and write a ros2_interfaces.manifest
 */
tasks.named<Jar>("jar") {
   val srcDir = file("ros2_interfaces")
   val manifestFile = File(buildDir, "ros2_interfaces.manifest")
   manifestFile.parentFile.mkdirs()

   // Relative order of dependence
   val packagePaths = listOf(
      projectDir.resolve("ros2_interfaces/example_interfaces"),
      projectDir.resolve("ros2_interfaces/rcl_interfaces/builtin_interfaces"),
      projectDir.resolve("ros2_interfaces/rcl_interfaces/lifecycle_msgs"),
      projectDir.resolve("ros2_interfaces/rcl_interfaces/rcl_interfaces"),
      projectDir.resolve("ros2_interfaces/rcl_interfaces/rosgraph_msgs"),
      projectDir.resolve("ros2_interfaces/rcl_interfaces/statistics_msgs"),
      projectDir.resolve("ros2_interfaces/common_interfaces/actionlib_msgs"),
      projectDir.resolve("ros2_interfaces/common_interfaces/diagnostic_msgs"),
      projectDir.resolve("ros2_interfaces/common_interfaces/geometry_msgs"),
      projectDir.resolve("ros2_interfaces/common_interfaces/nav_msgs"),
      projectDir.resolve("ros2_interfaces/common_interfaces/sensor_msgs"),
      projectDir.resolve("ros2_interfaces/common_interfaces/shape_msgs"),
      projectDir.resolve("ros2_interfaces/common_interfaces/std_msgs"),
      projectDir.resolve("ros2_interfaces/common_interfaces/stereo_msgs"),
      projectDir.resolve("ros2_interfaces/common_interfaces/trajectory_msgs"),
      projectDir.resolve("ros2_interfaces/common_interfaces/visualization_msgs"),
      projectDir.resolve("ros2_interfaces/jros2_example_interfaces")
   )

   doFirst {
      val fileList = packagePaths.flatMap { dir ->
         dir.walkTopDown()
            .filter { it.isFile }
            // Relativize path to srcDir for consistency
            .map { srcDir.toPath().relativize(it.toPath()).toString() }
            .toList()
      }
      manifestFile.writeText(fileList.joinToString(System.lineSeparator()))
   }

   from(srcDir)
   from(manifestFile)
}

dependencies {
   api("org.antlr:ST4:4.3.4")

   testImplementation("org.junit.jupiter:junit-jupiter:5.8.0")
}

tasks.test {
   useJUnitPlatform()
}