# Making a release
Increment ihmc.version in gradle.properties

Publish the parser and generator Gradle plugin
```
./gradlew publishGenerator -PpublishUrl=robotlabfiles
```
Upgrade the us.ihmc.jros2.generator Gradle plugin to the one you just published

Regenerate the common ROS 2 interfaces
```
./gradlew jros2GenerateDefaultInterfaces
```
Commit those generated files (if any were updated)

Publish the base library
```
./gradlew publish -PpublishUrl=robotlabfiles
```
Publish jros2-android (from the android/ project; credentials come from `~/.gradle/gradle.properties` as `publishUsername` / `publishPassword`)
```
cd android
./gradlew publish
cd ..
```

Ensure all the new versions are showing up at https://robotlabfiles.ihmc.us/repository

Commit the version bump with message ":bookmark: VERSION"

Create a tag with the version e.g. `git tag VERSION`

Push the commit and tag to develop.

Make a GitHub release.

Update the Wiki on GitHub with any required changes.
