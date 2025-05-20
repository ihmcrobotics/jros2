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
}

group = "us.ihmc"
version = "1.0.0"

gradlePlugin {
   plugins {
      create("jros2Generator") {
         id = "us.ihmc.jros2.generator"
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

dependencies {
   api("org.antlr:ST4:4.3.4")

   testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
   testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
   testImplementation("org.junit.platform:junit-platform-commons:1.9.2")
}

tasks.test {
   useJUnitPlatform()

   testLogging {
      events("passed", "failed", "skipped", "standard_out", "standard_error")
      exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
      showExceptions = true
      showCauses = true
      showStackTraces = true
   }
}