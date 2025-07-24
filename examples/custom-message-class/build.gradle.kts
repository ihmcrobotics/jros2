import us.ihmc.jros2.generator.task.jros2GenTask

plugins {
    id("java")
    id("application")
    id("us.ihmc.jros2.generator")
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
    mainClass.set("us.ihmc.CustomMessageClassTest")
}

tasks.register<jros2GenTask>("generateMessages") {
    packagePaths = listOf(
        projectDir.resolve("my_interfaces").absolutePath
    )

    // Generated files will go in the src/main/java source set
    outputDir = sourceSets["main"].java.srcDirs.find { it.name == "java" }.toString()

    typeToClass = mapOf("my_interfaces/MyPoint3D" to "us.ihmc.MyPoint3DMessage")
}