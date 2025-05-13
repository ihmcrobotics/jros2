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
}