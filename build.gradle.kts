plugins {
    id("java-library")
    id("maven-publish")
}

group = "us.ihmc"
version = "1.0.0"

sourceSets {
    create("common_interfaces") {
        java.srcDir("src/common_interfaces/java")
        resources.srcDir("src/common_interfaces/resources")
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
            artifactId = "fastddsjava"
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
}
