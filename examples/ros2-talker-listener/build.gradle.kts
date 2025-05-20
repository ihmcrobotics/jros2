plugins {
    id("java")
}

group = "us.ihmc"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
}
