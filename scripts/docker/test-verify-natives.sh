#!/usr/bin/env bash
#  Copyright 2026 Florida Institute for Human and Machine Cognition (IHMC)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Self-test of verify-natives.sh. Builds tiny stand-ins for libfastcdr/libfastdds/libjnifastddsjava (and
# libc++_shared for Android) with the host C compiler, checks that a correct platform directory passes, and that every
# defect the verifier exists to catch makes it fail for the right reason. Run in CI before the native builds, and
# locally after editing verify-natives.sh.
#
# usage: scripts/docker/test-verify-natives.sh
# needs: bash, a C compiler for the host (CC, default cc; x86_64 or aarch64 host), binutils (readelf, strip)

set -euo pipefail
export LC_ALL=C

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERIFY="$SCRIPT_DIR/verify-natives.sh"
CC=${CC:-cc}
STRIP=${STRIP:-strip}

case "$(uname -m)" in
   x86_64) LINUX=linux-x86_64; ANDROID=android-x86_64; OTHER=linux-arm64 ;;
   aarch64 | arm64) LINUX=linux-arm64; ANDROID=android-arm64-v8a; OTHER=linux-x86_64 ;;
   *) echo "test-verify-natives.sh: SKIP, no fixtures for host architecture $(uname -m)"; exit 0 ;;
esac

T=$(mktemp -d "${TMPDIR:-/tmp}/verify-natives-test.XXXXXX")
trap 'rm -rf "$T"' EXIT
SRC=$T/src
mkdir -p "$SRC"

cat > "$SRC/fastcdr.c" <<'EOF'
int fastcdr_fn(void) { return 2; }
EOF
cat > "$SRC/fastdds.c" <<'EOF'
extern int fastcdr_fn(void);
#ifdef WITH_SECURITY
const char fastdds_security_plugins[] = "builtin.PKI-DH builtin.AES-GCM-GMAC";
#endif
#ifdef WITH_OPENSSL_EXPORT
int SSL_CTX_new(void) { return 1; }
#endif
#ifdef WITH_ATOMIC_REF
extern int atomic_ref(void) __asm__("__atomic_exchange_1");
int fastdds_atomic(void) { return atomic_ref(); }
#endif
#ifdef WITH_OPENSSL_UNDEF
extern int ssl_ref(void) __asm__("EVP_MD_fetch");
int fastdds_ssl(void) { return ssl_ref(); }
#endif
int fastdds_fn(void) { return fastcdr_fn() + 1; }
EOF
cat > "$SRC/jni.c" <<'EOF'
#ifdef NO_FASTDDS
static int fastdds_fn(void) { return 3; }
#else
extern int fastdds_fn(void);
#endif
#ifndef NO_ONLOAD
int JNI_OnLoad(void *vm, void *reserved) { (void) vm; (void) reserved; return 0x00010008; }
#endif
int Java_us_ihmc_fastddsjava_natives_fastddsjava_foo(void) { return fastdds_fn(); }
void Java_us_ihmc_fastddsjava_natives_fastddsjava_bar_1baz(long long value) { (void) value; }
EOF
# Javadoc and comments that mention "native ... (" must not count as declarations.
cat > "$SRC/fastddsjava.java" <<'EOF'
package us.ihmc.fastddsjava.natives;

public class fastddsjava
{
   /**
    * A native method (declared below) that returns 3.
    */
   public static native int foo();

   // static native void commentedOut(long value);
   static native void bar_baz(long value);
}
EOF
sed 's/static native void bar_baz/public static native long qux(); static native void bar_baz/' "$SRC/fastddsjava.java" > "$SRC/fastddsjava-qux.java"
# Stand-ins for dependencies that must never be shipped or needed.
for stub in libatomic.so.1 libssl.so.3 libc.so.6; do
   mkdir -p "$T/stub"
   echo "int ${stub%%.*}_stub(void) { return 0; }" | sed 's/[+.-]/_/g' > "$T/stub/$stub.c"
   "$CC" -shared -fPIC -nostdlib -Wl,-soname,"$stub" "$T/stub/$stub.c" -o "$T/stub/$stub"
done

# Fixture knobs (environment of the build_* functions):
#   CDR_FLAGS DDS_FLAGS JNI_FLAGS  extra compiler/linker arguments per library
#   DDS_RUNPATH JNI_RUNPATH        runpath arguments (Linux defaults: $ORIGIN with new dtags)
#   JNI_UNLINKED=1                 do not link the JNI stand-in against libfastdds (and compile it with -DNO_FASTDDS)
#   NOSTRIP=1                      ship unstripped libraries
#   PAGE                           Android max-page-size (default 16384)
# Libraries are linked with --no-as-needed, so a NEEDED entry never depends on the compiler's default (Ubuntu's gcc
# passes --as-needed, other distributions do not).
link() { # out soname source extra-args...
   local out=$1 soname=$2 src=$3
   shift 3
   "$CC" -shared -fPIC -O1 -nostdlib -Wl,--no-as-needed -Wl,-soname,"$soname" -Wl,-z,noexecstack "$SRC/$src" "$@" \
      -o "$out"
}
finish() { # dir
   if [ "${NOSTRIP:-0}" != 1 ]; then
      for f in "$1"/*.so*; do "$STRIP" --strip-unneeded "$f"; done
   fi
}
jni_link() { # dir libfastdds-file -> link arguments of the JNI stand-in
   if [ "${JNI_UNLINKED:-0}" = 1 ]; then echo "-DNO_FASTDDS"; else echo "-L$1 -l:$2"; fi
}
# shellcheck disable=SC2016,SC2086,SC2046  # literal $ORIGIN; the *_FLAGS knobs and jni_link output are word lists
build_linux() { # dir
   local d=$1
   mkdir -p "$d"
   link "$d/libfastcdr.so.2" libfastcdr.so.2 fastcdr.c ${CDR_FLAGS:-}
   link "$d/libfastdds.so.3.6" libfastdds.so.3.6 fastdds.c -DWITH_SECURITY ${DDS_RUNPATH--Wl,--enable-new-dtags,-rpath,\$ORIGIN} \
      -L"$d" -l:libfastcdr.so.2 ${DDS_FLAGS:-}
   link "$d/libjnifastddsjava.so" libjnifastddsjava.so jni.c ${JNI_RUNPATH--Wl,--enable-new-dtags,-rpath,\$ORIGIN} \
      $(jni_link "$d" libfastdds.so.3.6) ${JNI_FLAGS:-}
   printf 'libfastcdr.so.2\nlibfastdds.so.3.6\nlibjnifastddsjava.so\n' > "$d/natives.list"
   finish "$d"
}
# shellcheck disable=SC2086,SC2046
build_android() { # dir
   local d=$1
   local page=("-Wl,-z,max-page-size=${PAGE:-16384}")
   mkdir -p "$d"
   echo 'int cxx_fn(void) { return 1; }' > "$SRC/cxx.c"
   link "$d/libc++_shared.so" libc++_shared.so cxx.c "${page[@]}"
   link "$d/libfastcdr.so" libfastcdr.so fastcdr.c "${page[@]}" -L"$d" -l:libc++_shared.so ${CDR_FLAGS:-}
   link "$d/libfastdds.so" libfastdds.so fastdds.c -DWITH_SECURITY "${page[@]}" -L"$d" -l:libfastcdr.so ${DDS_FLAGS:-}
   link "$d/libjnifastddsjava.so" libjnifastddsjava.so jni.c "${page[@]}" $(jni_link "$d" libfastdds.so) ${JNI_FLAGS:-}
   finish "$d"
}

PASS=0
FAILED=0
N=0
# expect <OK|FAIL> <description> <expected output substring or -> <platform> <dir> [java-source]
# (VAR=value prefixes on the call reach verify-natives.sh, e.g. JNI_ALLOW_MISSING)
expect() {
   local want=$1 what=$2 needle=$3 platform=$4 dir=$5 java=${6:-$SRC/fastddsjava.java} out line rc=0
   N=$((N + 1))
   out=$(bash "$VERIFY" "$platform" "$dir" "$java" 2>&1) || rc=$?
   local got=OK
   [ $rc = 0 ] || got=FAIL
   if [ "$got" = "$want" ] && { [ "$needle" = - ] || [[ "$out" == *"$needle"* ]]; }; then
      PASS=$((PASS + 1))
      printf '  ok    %-62s %s\n' "$what" "$got"
   else
      FAILED=$((FAILED + 1))
      printf '  FAIL  %-62s expected %s%s, got %s (exit %s)\n' "$what" "$want" \
         "$([ "$needle" = - ] || echo " with '$needle'")" "$got" "$rc"
      while IFS= read -r line; do echo "        | $line"; done <<< "$out"
   fi
}
fresh() { # name -> new empty fixture dir path
   echo "$T/case-$1"
}

echo "== verify-natives.sh self-test ($LINUX, $ANDROID fixtures, $("$CC" --version | head -1))"

# ---------------------------------------------------------------------------------------------- Linux
G=$(fresh linux-good); build_linux "$G"
expect OK "correct $LINUX directory" "verification OK" "$LINUX" "$G"
expect FAIL "wrong ELF machine ($OTHER)" "wrong machine" "$OTHER" "$G"

D=$(fresh stale); build_linux "$D"; echo stale > "$D/README.txt"
expect FAIL "stale foreign file" "unexpected file README.txt" "$LINUX" "$D"
D=$(fresh extra-lib); build_linux "$D"; cp "$D/libfastdds.so.3.6" "$D/libfastdds.so.3.6.2"
expect FAIL "stale second libfastdds" "exactly one libfastdds" "$LINUX" "$D"

D=$(fresh no-list); build_linux "$D"; rm "$D/natives.list"
expect FAIL "missing natives.list" "missing natives.list" "$LINUX" "$D"
D=$(fresh empty-list); build_linux "$D"; printf '# only a comment\n\n' > "$D/natives.list"
expect FAIL "natives.list without entries" "lists no libraries" "$LINUX" "$D"
D=$(fresh order); build_linux "$D"; printf 'libfastdds.so.3.6\nlibfastcdr.so.2\nlibjnifastddsjava.so\n' > "$D/natives.list"
expect FAIL "natives.list loads libfastdds before libfastcdr" "before its dependency libfastcdr.so.2" "$LINUX" "$D"
D=$(fresh jni-first); build_linux "$D"; printf 'libjnifastddsjava.so\nlibfastcdr.so.2\nlibfastdds.so.3.6\n' > "$D/natives.list"
expect FAIL "natives.list does not end with the JNI library" "must end with libjnifastddsjava.so" "$LINUX" "$D"
D=$(fresh short-list); build_linux "$D"; printf 'libfastdds.so.3.6\nlibjnifastddsjava.so\n' > "$D/natives.list"
expect FAIL "natives.list misses a library" "2 entries" "$LINUX" "$D"
D=$(fresh dup-list); build_linux "$D"; printf 'libfastcdr.so.2\nlibfastcdr.so.2\nlibjnifastddsjava.so\n' > "$D/natives.list"
expect FAIL "natives.list lists a library twice" "twice" "$LINUX" "$D"
D=$(fresh path-list); build_linux "$D"; printf 'libfastcdr.so.2\n../libfastdds.so.3.6\nlibjnifastddsjava.so\n' > "$D/natives.list"
expect FAIL "natives.list entry with a path" "not a plain file name" "$LINUX" "$D"
D=$(fresh comment-list); build_linux "$D"
printf '\xef\xbb\xbf# load order\r\n\r\n  libfastcdr.so.2  \r\nlibfastdds.so.3.6\r\n# JNI last\r\nlibjnifastddsjava.so' > "$D/natives.list"
expect OK "natives.list with BOM, comments, blanks, CRLF, spaces" "verification OK" "$LINUX" "$D"

D=$(fresh unstripped); NOSTRIP=1 CDR_FLAGS=-g build_linux "$D"
expect FAIL "unstripped libraries" "not stripped" "$LINUX" "$D"
D=$(fresh execstack); DDS_FLAGS=-Wl,-z,execstack build_linux "$D"
expect FAIL "executable stack" "executable stack" "$LINUX" "$D"
D=$(fresh rpath); DDS_RUNPATH=-Wl,--disable-new-dtags,-rpath,\$ORIGIN build_linux "$D"
expect FAIL "DT_RPATH instead of RUNPATH" "DT_RPATH" "$LINUX" "$D"
D=$(fresh leaked-runpath); JNI_RUNPATH=-Wl,--enable-new-dtags,-rpath,/home/runner/work/install/lib build_linux "$D"
expect FAIL "leaked build-path RUNPATH" "only \$ORIGIN is allowed" "$LINUX" "$D"
D=$(fresh no-runpath); DDS_RUNPATH='' build_linux "$D"
expect FAIL "libfastdds without RUNPATH \$ORIGIN" "RUNPATH must be \$ORIGIN" "$LINUX" "$D"
D=$(fresh soname); build_linux "$D"; mv "$D/libfastcdr.so.2" "$D/libfastcdr.so.2.3.5"
printf 'libfastcdr.so.2.3.5\nlibfastdds.so.3.6\nlibjnifastddsjava.so\n' > "$D/natives.list"
expect FAIL "file not named after its SONAME" "differs from the file name" "$LINUX" "$D"
D=$(fresh libatomic); DDS_FLAGS="-L$T/stub -l:libatomic.so.1" build_linux "$D"
expect FAIL "NEEDED libatomic.so.1" "NEEDED libatomic.so.1 is neither shipped" "$LINUX" "$D"
D=$(fresh atomic-undef); DDS_FLAGS=-DWITH_ATOMIC_REF build_linux "$D"
expect FAIL "undefined __atomic_exchange_1" "undefined __atomic_" "$LINUX" "$D"
D=$(fresh ssl-export); DDS_FLAGS=-DWITH_OPENSSL_EXPORT build_linux "$D"
expect FAIL "exported OpenSSL symbol" "OpenSSL symbols" "$LINUX" "$D"
D=$(fresh ssl-undef); DDS_FLAGS=-DWITH_OPENSSL_UNDEF build_linux "$D"
expect FAIL "OpenSSL not linked in (undefined EVP_MD_fetch)" "undefined OpenSSL symbols" "$LINUX" "$D"
D=$(fresh ssl-needed); DDS_FLAGS="-L$T/stub -l:libssl.so.3" build_linux "$D"
expect FAIL "dynamic libssl dependency" "dynamic OpenSSL dependency libssl.so.3" "$LINUX" "$D"
D=$(fresh jni-unlinked); JNI_UNLINKED=1 build_linux "$D"
expect FAIL "JNI library not linked against libfastdds" "does not link libfastdds.so.3.6" "$LINUX" "$D"
D=$(fresh no-onload); JNI_FLAGS=-DNO_ONLOAD build_linux "$D"
expect FAIL "JNI_OnLoad not exported" "JNI_OnLoad is not exported" "$LINUX" "$D"
expect FAIL "native method without JNI export" "missing JNI implementations for: qux" "$LINUX" "$G" "$SRC/fastddsjava-qux.java"
JNI_ALLOW_MISSING="nativeAbiVersion qux" \
   expect OK "missing native method allowed by JNI_ALLOW_MISSING" "allowed by JNI_ALLOW_MISSING" "$LINUX" "$G" "$SRC/fastddsjava-qux.java"
expect FAIL "missing fastddsjava.java" "not found" "$LINUX" "$G" "$T/nonexistent.java"

D=$(fresh no-security); build_linux "$D"
link "$D/libfastdds.so.3.6" libfastdds.so.3.6 fastdds.c -Wl,--enable-new-dtags,-rpath,\$ORIGIN -L"$D" -l:libfastcdr.so.2
"$STRIP" --strip-unneeded "$D/libfastdds.so.3.6"
expect FAIL "libfastdds without DDS-Security" "built without DDS-Security" "$LINUX" "$D"
EXPECT_SECURITY=0 expect OK "libfastdds without DDS-Security, EXPECT_SECURITY=0" "verification OK" "$LINUX" "$D"

# glibc symbol versions: a fixture linked against libc needs at least GLIBC_2.2.5 (x86_64) or GLIBC_2.17 (aarch64),
# so a lower limit must fail.
D=$(fresh glibc); build_linux "$D"
printf '%s\n' '#include <stdio.h>' 'int fastcdr_fn(void) { return puts("fastcdr"); }' > "$SRC/libc.c"
"$CC" -shared -fPIC -O1 -Wl,-soname,libfastcdr.so.2 -Wl,-z,noexecstack "$SRC/libc.c" -o "$D/libfastcdr.so.2"
"$STRIP" --strip-unneeded "$D/libfastcdr.so.2"
case "$(readelf -dW "$D/libfastcdr.so.2" | sed -n 's/.*(NEEDED).*\[\(.*\)\].*/\1/p' | tr '\n' ' ')" in
   *libc.so.6*)
      expect OK "libc symbols within the default GLIBC limit" "verification OK" "$LINUX" "$D"
      MAX_GLIBC=2.0 expect FAIL "libc symbols above MAX_GLIBC" "> 2.0" "$LINUX" "$D" ;;
   *libc.musl-*)
      # musl host: the libc fixture is a musl library instead.
      expect OK "musl libc dependency in a musl directory" "verification OK" "linux-musl-${LINUX#linux-}" "$D"
      expect FAIL "musl libc dependency in a glibc directory" "NEEDED libc.musl-" "$LINUX" "$D" ;;
   *) echo "  skip  GLIBC version checks (the host C library is neither glibc nor musl)" ;;
esac
D=$(fresh foreign-libc); DDS_FLAGS="-L$T/stub -l:libc.so.6" build_linux "$D"
expect FAIL "glibc library in a musl directory" "NEEDED libc.so.6 is neither shipped" "linux-musl-${LINUX#linux-}" "$D"

# ---------------------------------------------------------------------------------------------- Android
A=$(fresh android-good); build_android "$A"
expect OK "correct $ANDROID directory" "verification OK" "$ANDROID" "$A"
D=$(fresh android-4k); PAGE=4096 build_android "$D"
expect FAIL "4 KB aligned Android 64-bit libraries" "Android 16 KB page devices need" "$ANDROID" "$D"
D=$(fresh android-runpath); JNI_FLAGS=-Wl,--enable-new-dtags,-rpath,/home/runner/work/install/lib build_android "$D"
expect FAIL "Android JNI library with a RUNPATH" "on Android (leaks a build path" "$ANDROID" "$D"
D=$(fresh android-list); build_android "$D"; echo libjnifastddsjava.so > "$D/natives.list"
expect FAIL "natives.list in an Android jniLibs directory" "does not belong" "$ANDROID" "$D"
D=$(fresh android-no-libcxx); build_android "$D"; rm "$D/libc++_shared.so"
expect FAIL "Android directory without libc++_shared.so" "missing libc++_shared.so" "$ANDROID" "$D"
D=$(fresh android-glibc); DDS_FLAGS="-L$T/stub -l:libc.so.6" build_android "$D"
expect FAIL "Android library that needs glibc" "NEEDED libc.so.6 is neither shipped" "$ANDROID" "$D"
D=$(fresh android-jni-unlinked); JNI_UNLINKED=1 build_android "$D"
expect FAIL "Android JNI library not linked against libfastdds" "does not link libfastdds.so" "$ANDROID" "$D"

echo "== $PASS/$N cases passed"
[ $FAILED = 0 ]
