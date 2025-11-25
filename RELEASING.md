# Making a release
Increment ihmc.version in build.gradle.kts

Publish the parser and generator Gradle plugin
./gradlew publishGenerator -PpublishUrl=robotlabfiles

Upgrade the us.ihmc.jros2.generator Gradle plugin to the one you just published

Regenerate the common ROS 2 interfaces
./gradlew jros2GenerateDefaultInterfaces

Commit those generated files (if any were updated)

Publish the base library
./gradlew publish -PpublishUrl=robotlabfiles
