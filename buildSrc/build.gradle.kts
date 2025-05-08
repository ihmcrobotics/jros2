plugins {
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
