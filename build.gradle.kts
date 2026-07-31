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
import us.ihmc.jros2.generator.jros2GenTask

plugins {
   id("java-library")
   id("java-gradle-plugin")
   id("us.ihmc.ihmc-build")

   id("us.ihmc.jros2.generator") version "1.5.1"
}

ihmc {
   group = "us.ihmc"
   version = project.property("version").toString()
   vcsUrl = "https://github.com/ihmcrobotics/jros2"
   openSource = true

   configureDependencyResolution()
   configurePublications()
}

ihmc.sourceSetProject("generator").plugins.apply("java-gradle-plugin")
ihmc.sourceSetProject("generator").gradlePlugin {
   website.set("https://github.com/ihmcrobotics/jros2")
   vcsUrl.set("https://github.com/ihmcrobotics/jros2")

   plugins {
      create("jros2Generator") {
         id = "us.ihmc.jros2.generator"
         displayName = "jros2 Interface Generator"
         description = "Gradle plugin to generate Java ROS 2 interface classes from .msg files"
         tags.set(listOf("jros2", "ros2"))
         implementationClass = "us.ihmc.jros2.generator.jros2GeneratorPlugin"
      }
   }
}

java {
   sourceCompatibility = JavaVersion.VERSION_17
   targetCompatibility = JavaVersion.VERSION_17
}

// Avoid windows-1252 compile failures for UTF-8 test sources (e.g. wide-string literals).
tasks.withType<JavaCompile>().configureEach {
   options.encoding = "UTF-8"
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
}

parserDependencies {
}

generatorDependencies {
   api(gradleApi())

   api(ihmc.sourceSetProject("parser"))

   api("org.antlr:ST4:4.3.4") {
      isTransitive = true
   }
}

testDependencies {
   api(ihmc.sourceSetProject("parser"))
   api(ihmc.sourceSetProject("generator"))
}

tasks.register("publishGenerator") {
   dependsOn(ihmc.sourceSetProject("parser").tasks["publish"])
   dependsOn(ihmc.sourceSetProject("generator").tasks["publish"])
}

tasks.register<jros2GenTask>("jros2GenerateDefaultInterfaces") {
    // Make sure the git submodules are updated
    ProcessBuilder("git", "submodule", "update", "--init", "--recursive").directory(projectDir).start().waitFor()

    description = "Generate ROS 2 default interfaces source files"
    group = Char.MIN_VALUE + "jros2" // Hack to prevent Gradle from capitalizing jros2
    packagePaths = listOf(
        projectDir.resolve("ros2_interfaces").resolve("example_interfaces").absolutePath,

        // rcl_interfaces used as dependency to common_interfaces; we include it here
        projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("builtin_interfaces").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("lifecycle_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("rcl_interfaces").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("rosgraph_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("statistics_msgs").absolutePath,

        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("actionlib_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("diagnostic_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("geometry_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("nav_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("sensor_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("shape_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("std_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("stereo_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("trajectory_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("visualization_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("geometry2").resolve("tf2_msgs").absolutePath,

        projectDir.resolve("ros2_interfaces").resolve("jros2_example_interfaces").absolutePath,
    )
    outputDir = sourceSets["main"].java.srcDirs.find { it.name == "java-interfaces" }.toString()
}

// Copy runtime dependencies for Docker integration tests.
// Always create docker-libs/ even when runtimeClasspath is empty (no third-party jars),
// so the Docker image COPY step does not fail on a missing directory/glob.
tasks.register("copyDockerDependencies") {
   description = "Copy runtime dependencies for Docker integration tests"
   group = "build"

   doLast {
      val dir = layout.buildDirectory.dir("docker-libs").get().asFile
      dir.mkdirs()
      dir.listFiles()?.filter { it.isFile && it.extension == "jar" }?.forEach { it.delete() }
      configurations.runtimeClasspath.get().files.filter { it.extension == "jar" }.forEach { jar ->
         jar.copyTo(dir.resolve(jar.name), overwrite = true)
      }
   }
}

// Docker integration test for interface whitelisting (Linux only)
tasks.register<Exec>("testInterfaceWhitelistDocker") {
   description = "Run Docker-based integration tests for interface whitelisting (Linux only)"
   group = "verification"

   val isLinux = System.getProperty("os.name").lowercase().contains("linux")

   // Only run on Linux
   onlyIf {
      if (!isLinux) {
         println("INFO: Docker interface whitelist tests only run on Linux. Skipping on ${System.getProperty("os.name")}.")
      }
      isLinux
   }

   workingDir = projectDir
   commandLine = listOf("bash", "src/test/docker/test-interface-whitelist.sh")

   // Make test script executable
   doFirst {
      val testScript = projectDir.resolve("src/test/docker/test-interface-whitelist.sh")
      if (testScript.exists()) {
         testScript.setExecutable(true)
      }
   }

   // Show output
   standardOutput = System.out
   errorOutput = System.err
}
