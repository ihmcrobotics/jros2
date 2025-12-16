plugins {
    id("java")
    id("application")
}

group = "us.ihmc"
version = "1.0.0"

repositories {
   mavenCentral()
   maven { url = uri("https://robotlabfiles.ihmc.us/repository") }
}

dependencies {
    implementation("us.ihmc:jros2:1.1.3")
}

application {
    mainClass.set("us.ihmc.TalkerListener")
}