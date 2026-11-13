# Building the jros2 natives

The bundled native libraries (`libfastcdr`, `libfastdds` and the JNI library `libjnifastddsjava`) are built from the
sources pinned in [`../native-versions.env`](../native-versions.env): Fast-DDS 3.6.2, Fast-CDR 2.3.5,
foonathan memory (vendor 1.4.1) and OpenSSL 3.5.9. Every download is checksum-verified.

| Platforms | Script | Notes |
|---|---|---|
| `linux-x86_64`, `linux-arm64`, `linux-armhf`, `linux-riscv64` | `scripts/docker/build-natives.sh` | Ubuntu 20.04 container, so the libraries run on glibc 2.31 or newer |
| `linux-musl-x86_64`, `linux-musl-arm64` | `scripts/docker/build-natives.sh` | Alpine 3.24 container. Needs the `libstdc++` package at runtime (`apk add libstdc++`). Also tested to load on Alpine 3.22 (x86_64) |
| `android-arm64-v8a`, `android-armeabi-v7a`, `android-x86_64` | `scripts/docker/build-natives.sh` | NDK r30, API 26. The 64-bit ABIs are aligned for 16 KB pages |
| `macos-arm64`, `macos-x86_64`, `windows-x86_64`, `windows-arm64` | `scripts/cppbuild.bash` | Run on the host. macOS 12.0 is the minimum. DDS-Security is opt-in (`JROS2_SECURITY=ON`) |

The GitHub workflow `.github/workflows/build-natives.yml` (manual trigger) runs these scripts, verifies the
results and commits them.

## Docker build (Linux, musl, Android)

```bash
scripts/docker/build-natives.sh <platform> <repo-dir> <out-dir> [--jni-only] [--rebuild-deps]
# example
scripts/docker/build-natives.sh linux-x86_64 . /tmp/natives      # -> /tmp/natives/linux-x86_64/
```

* The first build of a platform also builds the dependencies: static OpenSSL, foonathan memory, Fast-CDR and
  Fast-DDS with DDS-Security. That takes 4 to 10 minutes at `JOBS=6`. For `linux-musl-arm64` on an x86_64 host the
  container runs under qemu and takes about an hour. Register qemu first with
  `docker run --privileged --rm tonistiigi/binfmt --install arm64`.
* The dependencies are cached in `$JROS2_WORK_ROOT/<platform>/deps` (default `~/.cache/jros2-natives`). A later run
  reuses the cache as long as the versions, the Dockerfile, `env.sh`, `deps.sh`, the Fast-DDS patches and `SECURITY`
  are unchanged.
* `deps.sh` applies jros2's Fast-DDS patches, `src/native/fastdds-patches/*.patch` in name order, to the pinned Fast-DDS
  sources with exact context (`patch -p1 -F 0`): a patch that no longer applies fails the build. They remove heap
  allocations from Fast-DDS's steady-state publish and receive paths, fix races on writers and readers of this process
  (0008, 0009) and fix a reader history lookup (0010).
  `scripts/cppbuild.bash` (macOS, Windows) applies the same patches.
  Comment-only edits do not count as a change.
* `--jni-only` rebuilds only `libjnifastddsjava` from `src/native/*.cpp` against the cached dependencies. It takes a
  few seconds and downloads nothing (Android still needs the NDK), so use it after changing the JNI C++ code. It
  fails if the platform's dependency cache is missing or was built from other versions or scripts.
* Output: `<out-dir>/<platform>/` holds exactly the files to ship.
  * Linux and musl: `libfastcdr.so.2`, `libfastdds.so.3.6`, `libjnifastddsjava.so` and `natives.list` (the load
    order). The file names are the SONAMEs, which musl's loader requires.
  * Android: `libc++_shared.so`, `libfastcdr.so`, `libfastdds.so` and `libjnifastddsjava.so`.

  Copy the directory to `src/main/resources/fastddsjava/native/<platform>/`, or for Android to
  `android/src/main/jniLibs/<abi>/`, replacing what is there.
* Useful environment variables are `JOBS`, `SECURITY=OFF`, `JROS2_WORK_ROOT`, `JROS2_ANDROID_NDK` (an extracted
  r30 NDK to use instead of downloading one) and `KEEP_BUILD_TREES=1`. `build-natives.sh --help` lists them all.

### Verification

`verify-natives.sh <platform> <dir> [fastddsjava.java]` runs at the end of every build, and again in CI. Any failed
check fails the build. It checks:

* the expected file set (nothing stale) and the `natives.list` order (read like the Java loader reads it: names are
  trimmed, blank lines and `#` comments are ignored);
* that `libfastdds` links `libfastcdr` and the JNI library links `libfastdds` (`NEEDED` by SONAME);
* ELF class, machine and float ABI; that the libraries are stripped; no executable stack; no text relocations;
* every `NEEDED` library is either shipped or a known system library, so there is no libatomic, libssl or libcrypto
  dependency;
* RUNPATH: on Linux, `$ORIGIN` for `libfastdds` and the JNI library and nothing else; on Android, none;
* on glibc, at most `GLIBC_2.31`, `GLIBCXX_3.4.28` and `CXXABI_1.3.12`;
* on Android arm64-v8a and x86_64, 16 KB LOAD alignment;
* no undefined `__atomic_*` symbols and no exported OpenSSL symbols;
* that DDS-Security is built in;
* that the JNI library exports every `native` method declared in `fastddsjava.java`.

`test-verify-natives.sh` is the verifier's own test. It builds tiny stand-in libraries with the host C compiler
(x86_64 or aarch64, glibc or musl) and checks that a correct directory passes and that each defect above (and a few
more, such as a leaked build-path RUNPATH or an Android JNI library that does not link `libfastdds`) makes the
verifier fail with the expected message. CI runs it before the native builds; run it after editing
`verify-natives.sh`.

## Host build (`cppbuild.bash`)

`scripts/cppbuild.bash` builds the same dependencies on the host and installs the result into the repository. It is
what CI uses for macOS and Windows, and developers can use it for any platform. The `scripts/build-*.bash` wrappers
select the target. Linux libraries built this way depend on the host's glibc. Use the Docker build for release
binaries.

* **macOS** (`MAC_COMPILE_ARM64=1` / `MAC_COMPILE_X86_64=1`): the deployment target is
  `MACOS_DEPLOYMENT_TARGET=12.0`, with `@loader_path` rpaths and `@rpath` install names. The libraries are shipped
  as `libfastcdr.2.dylib`, `libfastdds.3.6.dylib` and `libjnifastddsjava.dylib`, plus a `natives.list`.
* **Windows** (inside an MSVC developer shell; `WINDOWS_COMPILE_ARM64=1` for ARM64): `fastcdr-2.3.dll`,
  `fastdds-3.6.dll`, `jnifastddsjava.dll` and a `natives.list`.
* **DDS-Security** on macOS and Windows: set `JROS2_SECURITY=ON`, or use the workflow input
  `macos_windows_security`. OpenSSL is then built from source and linked statically. This is not exercised by the
  jros2 CI, so it is off by default.
