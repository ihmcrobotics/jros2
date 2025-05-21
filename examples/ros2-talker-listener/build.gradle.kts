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
    implementation(project(":"))
}

application {
    mainClass.set("us.ihmc.TalkerListener")
}