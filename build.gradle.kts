import us.ihmc.jros2.generator.task.jros2GenTask

plugins {
    id("java-library")
    id("maven-publish")
    id("us.ihmc.jros2.generator")
}

group = "us.ihmc"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    create("common_interfaces") {
        java.srcDir("src/common_interfaces/java")
        resources.srcDir("src/common_interfaces/resources")

        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }

    create("rcl_interfaces") {
        java.srcDir("src/rcl_interfaces/java")
        resources.srcDir("src/rcl_interfaces/resources")

        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }

    create("jros2_example_interfaces") {
        java.srcDir("src/jros2_example_interfaces/java")
        resources.srcDir("src/jros2_example_interfaces/resources")

        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }

    named("test") {
        compileClasspath += sourceSets["common_interfaces"].output
        runtimeClasspath += sourceSets["common_interfaces"].output

        compileClasspath += sourceSets["rcl_interfaces"].output
        runtimeClasspath += sourceSets["rcl_interfaces"].output

        compileClasspath += sourceSets["jros2_example_interfaces"].output
        runtimeClasspath += sourceSets["jros2_example_interfaces"].output
    }
}

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "jros2"
            version = project.version.toString()
        }
    }

    repositories {
        maven {
            val releasesRepo = uri("https://s01.oss.sonatype.org/content/repositories/releases")
            val snapshotsRepo = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepo else releasesRepo

            credentials {
                username = project.findProperty("publishUsername").toString()
                password = project.findProperty("publishPassword").toString()
            }
        }
    }
}

dependencies {
    // Transitive dependencies
    api("us.ihmc:javacpp:1.5.11-ihmc-2") {
        isTransitive = true
    }
    api("us.ihmc:ihmc-native-library-loader:2.0.4") {
        isTransitive = true
    }
    api("us.ihmc:log-tools:0.6.5") {
        isTransitive = true
    }
    // Match this version with YoVariables
    api("com.sun.xml.bind:jaxb-impl:4.0.5") {
        isTransitive = true
    }
    api("org.antlr:ST4:4.3.4") {
        isTransitive = true
    }

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

tasks.register<jros2GenTask>("generate_common_interfaces") {
    description = "Generate ros2/common_interfaces source files"
    group = Char.MIN_VALUE + "jros2" // Hack to prevent Gradle from capitalizing jros2
    packagePaths = listOf(
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("actionlib_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("diagnostic_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("geometry_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("nav_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("sensor_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("shape_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("std_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("stereo_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("trajectory_msgs").absolutePath,
        projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("visualization_msgs").absolutePath
    )
    outputDir = sourceSets["common_interfaces"].java.srcDirs.first().toString()
}

tasks.register<jros2GenTask>("generate_rcl_interfaces") {
    description = "Generate ros2/rcl_interfaces source files"
    group = Char.MIN_VALUE + "jros2" // Hack to prevent Gradle from capitalizing jros2
    packagePaths = listOf(
          projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("builtin_interfaces").absolutePath,
          projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("lifecycle_msgs").absolutePath,
          projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("rcl_interfaces").absolutePath,
          projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("rosgraph_msgs").absolutePath,
          projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("statistics_msgs").absolutePath,
    )
    outputDir = sourceSets["rcl_interfaces"].java.srcDirs.first().toString()
}
