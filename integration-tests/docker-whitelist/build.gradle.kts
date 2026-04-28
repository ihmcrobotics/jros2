plugins {
    id("java")
}

group = "us.ihmc.jros2"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

// Find jros2 JAR in parent project's build directory
val parentProjectDir = projectDir.parentFile.parentFile
val jros2BuildDir = parentProjectDir.resolve("build/libs")
val jros2JarPattern = "jros2-*.jar"

// Task to build parent jros2 JAR if it doesn't exist
tasks.register<Exec>("buildParentJar") {
    description = "Build parent jros2 JAR if not already built"
    group = "build"

    val jros2Jar = jros2BuildDir.listFiles()?.find { it.name.matches(Regex(jros2JarPattern)) }

    onlyIf {
        if (jros2Jar == null || !jros2Jar.exists()) {
            println("jros2 JAR not found in ${jros2BuildDir}, building parent project...")
            true
        } else {
            println("Found jros2 JAR: ${jros2Jar.name}")
            false
        }
    }

    workingDir = parentProjectDir
    commandLine = listOf("./gradlew", "jar")

    standardOutput = System.out
    errorOutput = System.err
}

// Copy runtime dependencies for Docker integration tests
tasks.register("copyDockerDependencies") {
    description = "Copy runtime dependencies for Docker integration tests"
    group = "build"

    dependsOn("buildParentJar")

    onlyIf {
        val isLinux = System.getProperty("os.name").lowercase().contains("linux")
        if (!isLinux) {
            println("INFO: Docker interface whitelist tests only run on Linux. Skipping on ${System.getProperty("os.name")}.")
        }
        isLinux
    }

    doLast {
        val dockerLibsDir = parentProjectDir.resolve("build/docker-libs")
        dockerLibsDir.deleteRecursively()
        dockerLibsDir.mkdirs()

        // Find all JARs that jros2 depends on by reading from parent's Gradle cache
        // We need: javacpp, ihmc-native-library-loader, jackson
        val gradleHome = System.getProperty("user.home") + "/.gradle/caches/modules-2/files-2.1"

        listOf(
            "org.bytedeco/javacpp/1.5.11",
            "us.ihmc/ihmc-native-library-loader/2.0.6",
            "com.fasterxml.jackson.core/jackson-databind/2.18.1",
            "com.fasterxml.jackson.core/jackson-core/2.18.1",
            "com.fasterxml.jackson.core/jackson-annotations/2.18.1",
            "com.fasterxml.jackson.dataformat/jackson-dataformat-xml/2.18.1",
            "com.fasterxml.woodstox/woodstox-core/7.1.0",
            "org.codehaus.woodstox/stax2-api/4.2.2"
        ).forEach { dep ->
            val depDir = File(gradleHome, dep.replace('/', File.separatorChar))
            if (depDir.exists()) {
                depDir.walkTopDown().filter { it.extension == "jar" && !it.name.contains("sources") && !it.name.contains("javadoc") }.forEach { jar ->
                    copy {
                        from(jar)
                        into(dockerLibsDir)
                    }
                }
            }
        }

        println("Copied dependencies to $dockerLibsDir")
    }
}

// Docker integration test for interface whitelisting (Linux only)
tasks.register<Exec>("testDockerWhitelist") {
    description = "Run Docker-based integration tests for interface whitelisting (Linux only)"
    group = "verification"

    val isLinux = System.getProperty("os.name").lowercase().contains("linux")

    // Only run on Linux
    onlyIf {
        if (!isLinux) {
            println("INFO: Docker interface whitelist tests only run on Linux. Skipping on ${System.getProperty("os.name")}.")
        }
        isLinux
    }

    workingDir = projectDir
    commandLine = listOf("bash", "test-interface-whitelist.sh")

    // Make test script executable
    doFirst {
        val testScript = projectDir.resolve("test-interface-whitelist.sh")
        if (testScript.exists()) {
            testScript.setExecutable(true)
        }
    }

    // Show output
    standardOutput = System.out
    errorOutput = System.err

    dependsOn("copyDockerDependencies")
}

// Map test task to testDockerWhitelist
tasks.test {
    dependsOn("testDockerWhitelist")
    setDependsOn(listOf("testDockerWhitelist"))
}

// Clean task
tasks.named<Delete>("clean") {
    delete("build")
}
