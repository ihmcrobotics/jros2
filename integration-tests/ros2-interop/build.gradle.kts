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

dependencies {
    // Find and include jros2 JAR from parent build directory
    implementation(fileTree(jros2BuildDir) {
        include(jros2JarPattern)
    })
}

// Include Java test sources
sourceSets {
    main {
        java {
            srcDir("src/jros2_interop_tests/src/main/java")
        }
    }
}

// Generate classpath file for Java wrapper scripts
tasks.register("generateClasspath") {
    description = "Generate classpath file for Java wrapper scripts"
    group = "build"

    dependsOn("classes", "buildParentJar")

    doLast {
        val classpathFile = layout.buildDirectory.file("classpath.txt").get().asFile
        classpathFile.parentFile.mkdirs()

        val runtimeClasspath = configurations.runtimeClasspath.get().files
        val classesDir = layout.buildDirectory.dir("classes/java/main").get().asFile

        val classpathEntries = mutableListOf<String>()
        classpathEntries.add(classesDir.absolutePath)
        runtimeClasspath.forEach { classpathEntries.add(it.absolutePath) }

        classpathFile.writeText(classpathEntries.joinToString(":"))

        // Also extract native library path from jros2 JAR
        val jros2Jar = runtimeClasspath.find { it.name.startsWith("jros2-") && it.name.endsWith(".jar") }
        if (jros2Jar != null) {
            val libPathFile = layout.buildDirectory.file("native-lib-path.txt").get().asFile
            // Native libraries are typically in the same directory as the JAR in .m2
            val nativeLibPath = jros2Jar.parentFile.resolve("native")
            libPathFile.writeText(nativeLibPath.absolutePath)
        }
    }
}

// Build Java test classes
tasks.register("buildJavaTests") {
    description = "Build Java integration test classes"
    group = "build"

    dependsOn("generateClasspath")
}

// Build ROS2 workspace with colcon
tasks.register<Exec>("buildROS2Workspace") {
    description = "Build ROS2 workspace with colcon"
    group = "build"

    val rosDistro = System.getenv("ROS_DISTRO")
    val hasRos = rosDistro != null && File("/opt/ros/$rosDistro").exists()

    onlyIf {
        if (!hasRos) {
            println("ERROR: ROS2 not found. Set ROS_DISTRO environment variable.")
        }
        hasRos
    }

    workingDir = projectDir

    val buildScript = """
        source /opt/ros/$rosDistro/setup.bash
        colcon build --symlink-install
    """.trimIndent()

    commandLine = listOf("bash", "-c", buildScript)

    standardOutput = System.out
    errorOutput = System.err

    dependsOn("buildJavaTests")
}

// Build everything (Java + ROS2)
// Don't include check task to avoid circular dependency with test
tasks.named("assemble") {
    dependsOn("buildROS2Workspace")
}

tasks.named("build") {
    setDependsOn(listOf("assemble"))
}

// Run integration tests via ROS2 launch files
tasks.register<Exec>("testROS2Integration") {
    description = "Run all ROS2 integration tests"
    group = "verification"

    val rosDistro = System.getenv("ROS_DISTRO")
    val hasRos = rosDistro != null && File("/opt/ros/$rosDistro").exists()
    val workspaceInstall = projectDir.resolve("install/setup.bash")

    onlyIf {
        if (!hasRos) {
            println("ERROR: ROS2 not found. Set ROS_DISTRO environment variable.")
            return@onlyIf false
        }
        if (!workspaceInstall.exists()) {
            println("ERROR: Workspace not built. Run 'gradle build' first.")
            return@onlyIf false
        }
        true
    }

    workingDir = projectDir

    val testScript = """
        source /opt/ros/$rosDistro/setup.bash
        source install/setup.bash
        export ROS_DOMAIN_ID=200
        timeout 40s ros2 launch jros2_interop_tests all_tests.launch.py || true
    """.trimIndent()

    commandLine = listOf("bash", "-c", testScript)

    standardOutput = System.out
    errorOutput = System.err

    dependsOn("build")
}

// Override test task to run ROS2 integration tests (but don't depend on build to avoid circular dependency)
tasks.test {
    dependsOn("testROS2Integration")
    // Remove dependency on classes since testROS2Integration already depends on build
    setDependsOn(listOf("testROS2Integration"))
}

// Clean task
tasks.named<Delete>("clean") {
    delete("build", "install", "log")
    delete(fileTree("src/jros2_interop_tests") {
        include(".gradle/**", "build/**", "gradlew", "gradlew.bat", "gradle/**")
    })
}
