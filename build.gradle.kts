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
    id("java-library")

    id("us.ihmc.ihmc-build")
}

ihmc {
    group = "us.ihmc"
    version = "1.0.1"
    vcsUrl = "https://github.com/ihmcrobotics/jros2"
    openSource = true

    configureDependencyResolution()
    configurePublications()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.javadoc {
    exclude("us/ihmc/fastddsjava/**")
}

sourceSets {
    named("main") {
        java.srcDirs("src/main/java-interfaces")
    }
}

mainDependencies {
    api("org.bytedeco:javacpp:1.5.11") {
        isTransitive = true
    }
    api("us.ihmc:ihmc-native-library-loader:2.0.6") {
        isTransitive = true
    }
    api("us.ihmc:log-tools:0.6.5") {
        isTransitive = true
    }
    // Match this version with YoVariables
    api("com.sun.xml.bind:jaxb-impl:4.0.5") {
        isTransitive = true
    }
}

parserDependencies {
}

generatorDependencies {
   api(ihmc.sourceSetProject("parser"))

   api("org.antlr:ST4:4.3.4") {
      isTransitive = true
   }
}

testDependencies {
   api(ihmc.sourceSetProject("parser"))
}

//tasks.register<jros2GenTask>("generate_default_interfaces") {
//    // Make sure the git submodules are updated
//    ProcessBuilder("git", "submodule", "update", "--init", "--recursive").directory(projectDir).start().waitFor()
//
//    description = "Generate ROS 2 default interfaces source files"
//    group = Char.MIN_VALUE + "jros2" // Hack to prevent Gradle from capitalizing jros2
//    packagePaths = listOf(
//        projectDir.resolve("ros2_interfaces").resolve("example_interfaces").absolutePath,
//
//        // rcl_interfaces used as dependency to common_interfaces; we include it here
//        projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("builtin_interfaces").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("lifecycle_msgs").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("rcl_interfaces").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("rosgraph_msgs").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("statistics_msgs").absolutePath,
//
//        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("actionlib_msgs").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("diagnostic_msgs").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("geometry_msgs").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("nav_msgs").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("sensor_msgs").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("shape_msgs").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("std_msgs").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("stereo_msgs").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("trajectory_msgs").absolutePath,
//        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("visualization_msgs").absolutePath,
//
//        projectDir.resolve("ros2_interfaces").resolve("jros2_example_interfaces").absolutePath,
//    )
//    outputDir = sourceSets["main"].java.srcDirs.find { it.name == "java-interfaces" }.toString()
//}
