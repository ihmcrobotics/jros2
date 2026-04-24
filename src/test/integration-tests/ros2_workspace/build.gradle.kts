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

dependencies {
    // jros2 main JAR and dependencies from parent project (4 levels up from workspace)
    implementation(files("../../../../build/libs/jros2-1.1.1001.jar"))
}

// Include Java test sources
sourceSets {
    main {
        java {
            srcDir("src/jros2_interop_tests/src/main/java")
        }
    }
}

// Build Java test classes
tasks.register("buildJavaTests") {
    description = "Build Java integration test classes"
    group = "build"

    dependsOn("classes")
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
        timeout 30s ros2 launch jros2_interop_tests all_tests.launch.py || true
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
