/*
 *  Copyright 2025 Florida Institute for Human and Machine Cognition (IHMC)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
import us.ihmc.jros2.generator.jros2GenTask

plugins {
   id("java-library")
   id("java-gradle-plugin")
   id("us.ihmc.ihmc-build")

   id("us.ihmc.jros2.generator") version "1.5.1"
}

ihmc {
   group = "us.ihmc"
   version = project.property("version").toString()
   vcsUrl = "https://github.com/ihmcrobotics/jros2"
   openSource = true

   configureDependencyResolution()
   configurePublications()
}

ihmc.sourceSetProject("generator").plugins.apply("java-gradle-plugin")
ihmc.sourceSetProject("generator").gradlePlugin {
   website.set("https://github.com/ihmcrobotics/jros2")
   vcsUrl.set("https://github.com/ihmcrobotics/jros2")

   plugins {
      create("jros2Generator") {
         id = "us.ihmc.jros2.generator"
         displayName = "jros2 Interface Generator"
         description = "Gradle plugin to generate Java ROS 2 interface classes from .msg files"
         tags.set(listOf("jros2", "ros2"))
         implementationClass = "us.ihmc.jros2.generator.jros2GeneratorPlugin"
      }
   }
}

java {
   sourceCompatibility = JavaVersion.VERSION_17
   targetCompatibility = JavaVersion.VERSION_17
}

// Avoid windows-1252 compile failures for UTF-8 test sources (e.g. wide-string literals).
tasks.withType<JavaCompile>().configureEach {
   options.encoding = "UTF-8"
}

tasks.javadoc {
   exclude("us/ihmc/fastddsjava/**")
}

sourceSets {
   named("main") {
      java.srcDirs("src/main/java-interfaces")
   }
}

mainDependencies {
}

parserDependencies {
}

generatorDependencies {
   api(gradleApi())

   api(ihmc.sourceSetProject("parser"))

   api("org.antlr:ST4:4.3.4") {
      isTransitive = true
   }
}

testDependencies {
   api(ihmc.sourceSetProject("parser"))
   api(ihmc.sourceSetProject("generator"))
   api("com.google.code.java-allocation-instrumenter:java-allocation-instrumenter:3.3.5")
}

// Allocation gate. Tests tagged "allocation" run only in the allocation tasks, never in the normal test task. Each runs every
// allocation test class in its own JVM (forkEvery = 1), so that no thread or endpoint of another class is active in a window:
// - allocationTest: a JVM started with the java-allocation-instrumenter agent. Every heap allocation made by bytecode on every
//   thread (JDK classes included) is reported to the test, independent of escape analysis and of JIT state; the gate also sums
//   HotSpot's allocated-bytes counters of all threads, which also see objects created by JNI code, the JVM and hidden classes.
// - allocationTestNoEA: no agent, -XX:-DoEscapeAnalysis, so the allocated-bytes counters cannot be fooled by escape analysis.
// - allocationTestIntraprocess and allocationTestIntraprocessNoEA: the same with Fast-DDS intraprocess delivery (FULL), a
//   process-wide setting; the first two tasks run with it OFF. System properties override FASTDDS_INTRAPROCESS_DELIVERY.
// allocationTest is finalized by the three others, which use its --tests selection, so ":jros2-test:allocationTest" runs the
// whole matrix. -Pjros2.allocation.diagnose adds -XX:-UseTLAB and a JFR pass that names the source of every allocation the
// agent cannot see (slow; for investigating a failure). -Pjros2.allocation.jit=default|c1|c2|interpreter runs the matrix
// under another JIT configuration than the default -Xbatch -XX:CompileThresholdScaling=0.1.
ihmc.sourceSetProject("test").tasks.named<Test>("test") {
   useJUnitPlatform {
      excludeTags("allocation")
   }
   // Gradle's ProjectBuilder (jros2GenTaskTest runs the generator task in a test project) defines classes in java.lang
   jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
val jros2AllocationTestTasks = listOf(
   Triple("allocationTest", true, false),
   Triple("allocationTestNoEA", false, false),
   Triple("allocationTestIntraprocess", true, true),
   Triple("allocationTestIntraprocessNoEA", false, true),
)
val jros2AllocationDiagnose = project.hasProperty("jros2.allocation.diagnose")
val jros2AllocationJit = (project.findProperty("jros2.allocation.jit") ?: "batch").toString()
for ((taskName, instrumented, intraprocess) in jros2AllocationTestTasks) {
   ihmc.sourceSetProject("test").tasks.register<Test>(taskName) {
      description = "Runs the allocation-free tests (tag 'allocation') " +
                    (if (instrumented) "under the java-allocation-instrumenter agent" else "without agent and with escape analysis disabled") +
                    ", intraprocess delivery " + (if (intraprocess) "FULL" else "OFF") +
                    (if (taskName == "allocationTest") "; finalized by the other allocation test tasks" else "")
      group = "verification"
      val defaultTest = ihmc.sourceSetProject("test").tasks.named<Test>("test").get()
      testClassesDirs = defaultTest.testClassesDirs
      classpath = defaultTest.classpath
      useJUnitPlatform {
         includeTags("allocation")
      }
      // The tag filter only runs inside the test JVM, and with forkEvery = 1 every candidate class costs a JVM start (about
      // 10 s with the agent): only classes that follow the naming convention of the allocation tests are candidates.
      // AllocationTaskSelectionTest (normal suite) checks that every class tagged "allocation" matches these patterns.
      include("**/*AllocationTest.class", "**/allocation/**/*Test.class")
      maxParallelForks = 1
      forkEvery = 1
      val mode = if (instrumented) "instrumented" else "noEA"
      systemProperty("jros2.allocation.mode", mode)
      systemProperty("fastdds.intraprocess.delivery", intraprocess.toString())
      systemProperty("jros2.allocation.reportDir", layout.buildDirectory.dir("allocation-gate/$taskName").get().asFile.absolutePath)
      // JIT warm-up within the warm-up iterations: HotSpot resolves all string constants of a class on the calling thread
      // when it first queues one of the class's methods for C2, a one-time allocation per class. With -Xbatch methods are
      // compiled synchronously, so these requests happen at fixed invocation counts instead of whenever the compile queue
      // lets the load-dependent thresholds be reached, and C2 compiles after a tenth of the usual invocations so that the
      // slow large-message tests get there too. This changes when the JIT compiles, not what a window measures.
      // -Pjros2.allocation.jit=default|c1|c2|interpreter runs the gates with the default JIT settings, C1 only, C2 only or
      // the interpreter instead (allocation-free code passes all of them; the interpreter is slow).
      when (jros2AllocationJit) {
         "batch" -> jvmArgs("-Xbatch", "-XX:CompileThresholdScaling=0.1")
         "default" -> {}
         "c1" -> jvmArgs("-XX:TieredStopAtLevel=1")
         "c2" -> jvmArgs("-XX:-TieredCompilation")
         "interpreter" -> jvmArgs("-Xint")
         else -> throw GradleException("Unknown jros2.allocation.jit: $jros2AllocationJit (batch, default, c1, c2 or interpreter)")
      }
      // The gate creates the string constants of the test classes before the warm-up (AllocationGate.CONSTANT_POOL_ACCESS_OPTIONS).
      jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.reflect=ALL-UNNAMED")
      // Idle monitors are deflated every millisecond, so that a contended monitor is inflated again (a malloc) inside every
      // window, where the monitor pass sees it (AllocationGate.MONITOR_DEFLATION_OPTIONS).
      jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:GuaranteedSafepointInterval=1", "-XX:GuaranteedAsyncDeflationInterval=1",
              "-XX:AsyncDeflationInterval=1")
      if (!instrumented) {
         jvmArgs("-XX:-DoEscapeAnalysis")
      }
      if (jros2AllocationDiagnose) {
         jvmArgs("-XX:-UseTLAB")
         systemProperty("jros2.allocation.diagnose", "true")
      }
      val testTasks = ihmc.sourceSetProject("test").tasks
      doFirst {
         if (instrumented) {
            val agentJar = classpath.filter { it.name.startsWith("java-allocation-instrumenter") }.singleFile
            jvmArgs("-javaagent:${agentJar.absolutePath}")
         }
         if (taskName != "allocationTest") {
            // Same --tests selection as allocationTest, which this task finalizes. Gradle keeps the --tests patterns of the
            // command line apart from the build script's include patterns, and applies them to the named task only.
            val first = testTasks.getByName("allocationTest") as Test
            filter.setIncludePatterns(*first.filter.includePatterns.toTypedArray())
            val firstCommandLine = (first.filter as org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter).commandLineIncludePatterns
            if (firstCommandLine.isNotEmpty()) {
               (filter as org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter).setCommandLineIncludePatterns(firstCommandLine)
            }
         }
      }
   }
}
ihmc.sourceSetProject("test").tasks.named<Test>("allocationTest") {
   finalizedBy(jros2AllocationTestTasks.drop(1).map { it.first })
}

// Code coverage (JaCoCo). The test task of the test project records execution data (src/test/build/jacoco/test.exec);
// tests that start child JVMs pass the agent on (fastddsjavaTestTools.coverageAgentArguments), so their code is recorded
// too. :jros2-test:jacocoTestReport writes XML and HTML to src/test/build/reports/jacoco/ and covers exactly the classes the
// tests load: the main classes of this project (us.ihmc.jros2, us.ihmc.fastddsjava and the bundled interfaces of
// src/main/java-interfaces) and the classes of the parser and generator source sets. Neither task runs the tests: run
// :jros2-test:test first (CI runs both with -x :jros2-test:test after its test step).
// The allocation test tasks run without the JaCoCo agent: it would rewrite classes and allocate in them.
val jros2CoverageSourceSets = listOf(project, ihmc.sourceSetProject("parser"), ihmc.sourceSetProject("generator")).map {
   it.extensions.getByType<SourceSetContainer>().named("main")
}
// Generated code that the coverage thresholds leave out: the bundled interfaces (one package per ROS 2 package) and the
// classes generated from the Fast-DDS XML schema.
val jros2CoverageGeneratedClasses = (projectDir.resolve("src/main/java-interfaces").listFiles() ?: emptyArray())
   .filter { it.isDirectory }
   .map { "${it.name}/**" } + "us/ihmc/fastddsjava/profiles/gen/**"

ihmc.sourceSetProject("test").plugins.apply("jacoco")
for (taskName in jros2AllocationTestTasks.map { it.first }) {
   ihmc.sourceSetProject("test").tasks.named<Test>(taskName) {
      extensions.configure<JacocoTaskExtension> {
         isEnabled = false
      }
   }
}
ihmc.sourceSetProject("test").tasks.named<JacocoReport>("jacocoTestReport") {
   description = "Generates the JaCoCo coverage report (XML and HTML) of the test task"
   mustRunAfter(ihmc.sourceSetProject("test").tasks.named("test"))
   sourceDirectories.setFrom(jros2CoverageSourceSets.map { sourceSet -> sourceSet.map { it.allJava.srcDirs } })
   classDirectories.setFrom(jros2CoverageSourceSets.map { sourceSet -> sourceSet.map { it.output.classesDirs } })
   reports {
      xml.required = true
      html.required = true
      csv.required = false
   }
}
// Thresholds for the hand-written code, set a few points below the coverage measured on Linux x86_64 with ROS 2 Jazzy (October
// 2026: 93.4 % of the lines and 86.4 % of the branches; 93.0 % and 85.6 % with intraprocess delivery and without the optional
// SROS2 research keystore and Docker image, as on the CI runners). Platforms without the ROS 2 CLI or with ABI v1 natives
// (macOS, Windows) cover less and are not meant to be checked.
ihmc.sourceSetProject("test").tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
   description = "Verifies the JaCoCo coverage of the hand-written code (generated interfaces and XML schema classes excluded)"
   mustRunAfter(ihmc.sourceSetProject("test").tasks.named("test"))
   sourceDirectories.setFrom(jros2CoverageSourceSets.map { sourceSet -> sourceSet.map { it.allJava.srcDirs } })
   classDirectories.setFrom(jros2CoverageSourceSets.map { sourceSet ->
      sourceSet.map { it.output.classesDirs.asFileTree.matching { exclude(jros2CoverageGeneratedClasses) } }
   })
   violationRules {
      // All hand-written code
      rule {
         limit {
            counter = "LINE"
            minimum = "0.90".toBigDecimal()
         }
         limit {
            counter = "BRANCH"
            minimum = "0.82".toBigDecimal()
         }
      }
      // Every hand-written source file. CDRDeserializationException is left out: half of its 4 lines are a (message, cause)
      // constructor that jros2 does not use.
      rule {
         element = "SOURCEFILE"
         excludes = listOf("us/ihmc/fastddsjava/cdr/CDRDeserializationException.java")
         limit {
            counter = "LINE"
            minimum = "0.65".toBigDecimal()
         }
      }
   }
}

tasks.register("publishGenerator") {
   dependsOn(ihmc.sourceSetProject("parser").tasks["publish"])
   dependsOn(ihmc.sourceSetProject("generator").tasks["publish"])
}

// ROS 2 packages whose messages are bundled with jros2 (src/main/java-interfaces)
val jros2DefaultInterfacePackagePaths = listOf(
    projectDir.resolve("ros2_interfaces").resolve("example_interfaces").absolutePath,

    // rcl_interfaces used as dependency to common_interfaces; we include it here
    projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("builtin_interfaces").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("lifecycle_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("rcl_interfaces").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("rosgraph_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("rcl_interfaces").resolve("statistics_msgs").absolutePath,

    projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("actionlib_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("diagnostic_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("geometry_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("nav_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("sensor_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("shape_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("std_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("stereo_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("trajectory_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("common_interfaces").resolve("visualization_msgs").absolutePath,
    projectDir.resolve("ros2_interfaces").resolve("geometry2").resolve("tf2_msgs").absolutePath,

    projectDir.resolve("ros2_interfaces").resolve("jros2_example_interfaces").absolutePath,
)

// Generates with the published generator plugin (see the plugins block for its version)
tasks.register<jros2GenTask>("jros2GenerateDefaultInterfaces") {
    // Make sure the git submodules are updated
    ProcessBuilder("git", "submodule", "update", "--init", "--recursive").directory(projectDir).start().waitFor()

    description = "Generate ROS 2 default interfaces source files with the published generator plugin. Until a plugin newer than 1.5.1 is " +
                  "published this drops the type hashes and CDR fixes of the bundled classes; use jros2GenerateDefaultInterfacesLocal instead"
    group = Char.MIN_VALUE + "jros2" // Hack to prevent Gradle from capitalizing jros2
    packagePaths = jros2DefaultInterfacePackagePaths
    outputDir = sourceSets["main"].java.srcDirs.find { it.name == "java-interfaces" }.toString()
}

// Generates with the generator and parser sources of this repository (src/generator, src/parser) instead of the
// published plugin, and refreshes the bundled definitions resource that the generator uses to compute the type hashes
// of user messages that reference the bundled types.
val jros2GeneratorLocalClasspath: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    jros2GeneratorLocalClasspath(project(ihmc.sourceSetProject("generator").path))
}

tasks.register<JavaExec>("jros2GenerateDefaultInterfacesLocal") {
    description = "Generate ROS 2 default interfaces source files with the generator sources of this repository"
    group = Char.MIN_VALUE + "jros2" // Hack to prevent Gradle from capitalizing jros2

    classpath = jros2GeneratorLocalClasspath
    mainClass.set("us.ihmc.jros2.generator.jros2GeneratorMain")

    val generatorArgs = mutableListOf(
        "--output-dir", projectDir.resolve("src").resolve("main").resolve("java-interfaces").absolutePath,
        "--bundled-definitions-output", projectDir.resolve("src").resolve("generator").resolve("resources").resolve("jros2_bundled_interfaces.txt").absolutePath,
        "--no-bundled-definitions",
    )
    jros2DefaultInterfacePackagePaths.forEach { generatorArgs.addAll(listOf("--package", it)) }
    args = generatorArgs

    doFirst {
        // Make sure the git submodules are checked out (they pin the humble branch of the interface repositories)
        ProcessBuilder("git", "submodule", "update", "--init", "--recursive").directory(projectDir).inheritIO().start().waitFor()
    }
}

// Copy runtime dependencies for Docker integration tests.
// Always create docker-libs/ even when runtimeClasspath is empty (no third-party jars),
// so the Docker image COPY step does not fail on a missing directory/glob.
tasks.register("copyDockerDependencies") {
   description = "Copy runtime dependencies for Docker integration tests"
   group = "build"

   doLast {
      val dir = layout.buildDirectory.dir("docker-libs").get().asFile
      dir.mkdirs()
      dir.listFiles()?.filter { it.isFile && it.extension == "jar" }?.forEach { it.delete() }
      configurations.runtimeClasspath.get().files.filter { it.extension == "jar" }.forEach { jar ->
         jar.copyTo(dir.resolve(jar.name), overwrite = true)
      }
   }
}

// Docker integration test for interface whitelisting (Linux only)
tasks.register<Exec>("testInterfaceWhitelistDocker") {
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
   commandLine = listOf("bash", "src/test/docker/test-interface-whitelist.sh")

   // Make test script executable
   doFirst {
      val testScript = projectDir.resolve("src/test/docker/test-interface-whitelist.sh")
      if (testScript.exists()) {
         testScript.setExecutable(true)
      }
   }

   // Show output
   standardOutput = System.out
   errorOutput = System.err
}
