import java.util.Properties

plugins {
   id("com.android.library")
   id("maven-publish")
}

// Read version from parent project's gradle.properties
val parentProperties = Properties()
file("../gradle.properties").inputStream().use { parentProperties.load(it) }

group = "us.ihmc"
version = parentProperties.getProperty("version")

android {
   namespace = "us.ihmc.jros2"
   compileSdk = 35

   defaultConfig {
      minSdk = 26

      consumerProguardFiles("consumer-rules.pro")
   }

   buildTypes {
      release {
         isMinifyEnabled = false
         proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      }
   }

   compileOptions {
      sourceCompatibility = JavaVersion.VERSION_17
      targetCompatibility = JavaVersion.VERSION_17
   }

   sourceSets {
      getByName("main") {
         java.srcDirs("../src/main/java", "../src/main/java-interfaces")
         resources.srcDirs("../src/main/resources")
      }
   }

   packaging {
      resources.excludes.add("**/fastddsjava/native/**")
      jniLibs {
         pickFirsts.add("**/libfastcdr.so*")
         pickFirsts.add("**/libfastdds.so*")
         pickFirsts.add("**/libjnifastddsjava.so")
      }
   }
}

dependencies {
   api("org.bytedeco:javacpp:1.5.11")
   api("us.ihmc:ihmc-native-library-loader:2.0.6")
   // Match Jackson version with ihmc-robot-data-logger
   api("com.fasterxml.jackson.core:jackson-databind:2.18.1")
   api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.1")
   // StAX API and implementation for Android (javax.xml.stream package)
   api("javax.xml.stream:stax-api:1.0-2")
   api("com.fasterxml.woodstox:woodstox-core:6.7.0")
}

tasks.register("copyLibcppShared") {
   doLast {
      val jniLibsDir = file("src/main/jniLibs")
      jniLibsDir.mkdirs()

      // Find NDK directory
      val sdkDir = android.sdkDirectory
      val ndkDir = file("$sdkDir/ndk").listFiles()?.maxByOrNull { it.name } // Get latest NDK version

      if (ndkDir != null && ndkDir.exists()) {
         println("Found NDK: $ndkDir")
         // Copy libc++_shared.so for each ABI
         listOf("arm64-v8a" to "aarch64-linux-android", "x86_64" to "x86_64-linux-android").forEach { (abi, triple) ->
            val libcppPath = file("$ndkDir/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$triple/libc++_shared.so")
            if (libcppPath.exists()) {
               val abiDir = file("$jniLibsDir/$abi")
               abiDir.mkdirs()
               copy {
                  from(libcppPath)
                  into(abiDir)
               }
               println("Copied libc++_shared.so for $abi from $libcppPath")
            } else {
               println("libc++_shared.so not found at $libcppPath")
            }
         }
      } else {
         println("NDK not found in $sdkDir/ndk")
      }
   }
}

tasks.named("preBuild") {
   dependsOn("copyLibcppShared")
}

afterEvaluate {
   publishing {
      publications {
         create<MavenPublication>("release") {
            groupId = "us.ihmc"
            artifactId = "jros2-android"
            version = project.version.toString()

            artifact(tasks.named("bundleReleaseAar").get().outputs.files.single())

            pom.withXml {
               val dependenciesNode = asNode().appendNode("dependencies")
               configurations.getByName("api").allDependencies.forEach { dep ->
                  val dependencyNode = dependenciesNode.appendNode("dependency")
                  dependencyNode.appendNode("groupId", dep.group)
                  dependencyNode.appendNode("artifactId", dep.name)
                  dependencyNode.appendNode("version", dep.version)
                  dependencyNode.appendNode("scope", "compile")
               }
            }
         }
      }
   }

   tasks.named("publishReleasePublicationToMavenLocal") {
      dependsOn("bundleReleaseAar")
   }
}