# Making a release

## Before the release

Make sure the bundled native libraries are current. Rebuild them when `src/native/*.cpp`, `src/native/*.h`,
`src/native/fastdds-patches/*.patch` (jros2's patches of Fast-DDS), `scripts/native-versions.env` (Fast-DDS, Fast-CDR,
OpenSSL, NDK versions) or the build scripts changed since the last build, and for every OpenSSL security release:

- Run the **Build natives** workflow (`.github/workflows/build-natives.yml`, Actions tab, "Run workflow") on the branch to
  release. It commits the rebuilt libraries to that branch. Inputs:
  - `platforms`: `all`, `linux`, `android`, `macos` or `windows`.
  - `macos_windows_security`: build macOS and Windows with DDS-Security (OpenSSL built from source and linked
    statically). Off by default; jros2's CI does not exercise it.
  - `musl_arm64_runner`: `ubuntu-24.04-arm` (native) or `ubuntu-24.04` (qemu, about 10x slower).
- The workflow builds, in Docker with pinned and checksummed sources, Fast-DDS with DDS-Security and a private static
  OpenSSL for Linux x86_64, arm64, armhf and riscv64 (glibc 2.31 baseline), Linux musl x86_64 and arm64, and Android
  arm64-v8a, armeabi-v7a and x86_64 (NDK r30, API 26, 16 KB page alignment on the 64-bit ABIs). macOS and Windows are built
  with `scripts/cppbuild.bash`. Both apply the Fast-DDS patches in `src/native/fastdds-patches` (name order, exact
  context: a patch that no longer applies to the pinned Fast-DDS fails the build). Every platform directory is replaced as a whole, and `scripts/docker/verify-natives.sh`
  checks every Linux and Android result. See [scripts/docker/README.md](scripts/docker/README.md) for local builds.
- For 1.6.0: the libraries of every platform must be built with all ten Fast-DDS patches. jros2's intraprocess delivery
  mode (`USER_DATA_ONLY`) can crash under endpoint churn without patches 0008 and 0009, so rebuild with `platforms`: `all` after
  adding a patch, and do not release with libraries from before it. The macOS and Windows libraries are built without
  DDS-Security (`macos_windows_security` off). If that changes, update `README.md` (the note under "Supported platforms" and
  the SROS2 section) and the "Known limitations" of `CHANGELOG.md`.
- Changing `scripts/docker/env.sh`, `deps.sh`, a Dockerfile, `native-versions.env` (other than in comments) or a Fast-DDS
  patch invalidates the dependency cache: a local `build-natives.sh --jni-only` run then refuses, and a full build is needed.

Make sure the **Test** workflow (`.github/workflows/gradle-test.yml`) passes on the branch: the Linux ROS 2 distro
matrix, Windows (x86_64 and ARM), macOS, Android Lint and R8 checks, the Android emulator tests, Alpine (musl), the riscv64 native check and
the allocation gates.

Local checks (a ROS 2 installation in `/opt/ros/$ROS_DISTRO` enables the CLI interop tests):
```
ROS_DISTRO=jazzy ROS_DOMAIN_ID=42 ./gradlew :jros2-test:test
ROS_DISTRO=jazzy ROS_DOMAIN_ID=42 FASTDDS_INTRAPROCESS_DELIVERY=true ./gradlew :jros2-test:test --rerun
./gradlew :jros2-test:allocationTest
./gradlew :jros2-test:jacocoTestReport :jros2-test:jacocoTestCoverageVerification
```
`--rerun` is needed because Gradle does not rerun the tests when only an environment variable changes.

Android checks (from `android/`; `connectedDebugAndroidTest` needs a running emulator or device):
```
cd android
./gradlew assembleRelease verifyJniLibs verifyAar lintRelease
bash src/androidTest/sros2/make-test-keystore.sh /tmp/sros2-keystore
./gradlew connectedDebugAndroidTest -Pjros2.sros2Keystore=/tmp/sros2-keystore \
   -Pandroid.testInstrumentationRunnerArguments.expectedAbiVersion=2 \
   -Pandroid.testInstrumentationRunnerArguments.expectSecurity=true
cd ..
```

## Release steps

Increment `version` in `gradle.properties`. The jros2-android AAR takes its version from there too.

Regenerate the bundled ROS 2 interfaces with the generator sources of this repository
```
./gradlew jros2GenerateDefaultInterfacesLocal
```
This updates `src/main/java-interfaces` and the generator resource `src/generator/resources/jros2_bundled_interfaces.txt`,
from which the generator computes the type hashes of user messages that reference bundled types. It checks out the git
submodules in `ros2_interfaces` first. Commit those generated files (if any were updated). Do this before publishing the
generator, so that the published generator contains the current resource.

Publish the parser and generator Gradle plugin
```
./gradlew publishGenerator -PpublishUrl=robotlabfiles
```
Only after it is published, upgrade the `us.ihmc.jros2.generator` plugin version to the one you just published: in the
`plugins` block of `build.gradle.kts` (the build cannot resolve an unpublished version) and in
`examples/custom-message-class/build.gradle.kts`.

Optionally regenerate with the published plugin and check that nothing changes (`git status` stays clean)
```
./gradlew jros2GenerateDefaultInterfaces
```
Do not use `jros2GenerateDefaultInterfaces` while the root build still applies plugin 1.5.1: that plugin drops the type
hashes and the CDR fixes of the bundled classes.

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

Update the version in the dependency snippets of `README.md` and in the `examples/*/build.gradle.kts` files. Move the
"Unreleased" section of `CHANGELOG.md` under the new version, and remove the README note about the `develop` branch
documenting an unreleased version.

Commit the version bump with message ":bookmark: VERSION"

Create a tag with the version e.g. `git tag VERSION`

Push the commit and tag to develop.

Make a GitHub release.

Update the Wiki on GitHub with any required changes.
