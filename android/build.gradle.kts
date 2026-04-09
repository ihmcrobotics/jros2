plugins {
   id("com.android.library")
   id("maven-publish")
}

group = "us.ihmc"
version = "1.1.597"

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

   // Jackson for XML marshalling (works on all platforms including Android)
   api("com.fasterxml.jackson.core:jackson-databind:2.18.1")
   api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.1")

   // StAX API and implementation for Android (javax.xml.stream package)
   api("javax.xml.stream:stax-api:1.0-2")
   api("com.fasterxml.woodstox:woodstox-core:6.7.0")
}

tasks.register("copyNativeLibs") {
   doLast {
      val jniLibsDir = file("src/main/jniLibs")
      jniLibsDir.mkdirs()

      listOf("android-arm64-v8a" to "arm64-v8a", "android-x86_64" to "x86_64").forEach { (src, dst) ->
         val srcDir = file("../src/main/resources/fastddsjava/native/$src")
         val dstDir = file("$jniLibsDir/$dst")
         dstDir.mkdirs()

         srcDir.listFiles()?.forEach { file ->
            val targetName = file.name.replaceFirst(Regex("\\.so\\..*"), ".so")
            copy {
               from(file)
               into(dstDir)
               rename { targetName }
            }
         }
      }
   }
}

tasks.named("preBuild") {
   dependsOn("copyNativeLibs")
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