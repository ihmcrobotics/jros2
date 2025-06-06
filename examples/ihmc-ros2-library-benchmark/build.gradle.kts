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
    implementation("us.ihmc:ros2-library:1.2.3")
    implementation("us.ihmc:ros2-common-interfaces:1.2.3")
}

application {
    mainClass.set("us.ihmc.ihmcros2libraryBenchmark")
}