plugins {
   id("java")
   id("application")
}

group = "us.ihmc"
version = "1.0.0"

repositories {
   mavenCentral()
}

dependencies {
   implementation("us.ihmc:jros2:1.0.0")

   implementation("org.bytedeco:javacv-platform:1.5.11")
}

application {
   mainClass.set("us.ihmc.WebcamPublisher")
}
