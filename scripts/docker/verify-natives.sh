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

# Verifies one staged platform directory of jros2 natives (ELF platforms: Linux glibc/musl and Android).
# Needs only bash, GNU or LLVM readelf and POSIX tools, so it runs in the build containers and on CI runners.
#
# usage: verify-natives.sh <platform> <dir> [path/to/fastddsjava.java]
#
# Checks (any failure exits 1):
#   - expected file set (no stale files), natives.list (Linux: complete, JNI last, dependency order; parsed like
#     fastddsjavaNativeLibrary.parseManifest: names trimmed, blank lines and '#' comments ignored)
#   - libfastdds links libfastcdr and the JNI library links libfastdds (NEEDED by SONAME)
#   - ELF class/machine/float ABI of every library, shared object, stripped (no .symtab / .debug_*)
#   - non-executable stack (glibc >= 2.41 refuses to dlopen libraries that need one), no TEXTREL
#   - every NEEDED library is shipped in the directory or is an allowed system library
#     (so no libatomic, libssl or libcrypto dependency can slip through)
#   - RUNPATH: Linux libfastdds/libjnifastddsjava exactly $ORIGIN, nothing else anywhere; Android: none
#   - glibc targets: max GLIBC_2.31, GLIBCXX_3.4.28, CXXABI_1.3.12 (Ubuntu 20.04), no GLIBC_PRIVATE
#   - Android arm64-v8a/x86_64: every LOAD segment aligned to >= 16 KB
#   - no undefined __atomic_* (would need libatomic at runtime), no exported and no undefined OpenSSL symbols
#   - EXPECT_SECURITY=1 (default): libfastdds was built with DDS-Security
#   - JNI library exports JNI_OnLoad and every native method declared in fastddsjava.java
#     (method names listed in JNI_ALLOW_MISSING may be absent, e.g. ABI v2 methods with older C++ sources)

# shellcheck disable=SC2016  # '$ORIGIN' is a literal RUNPATH value
set -euo pipefail
export LC_ALL=C

PLATFORM=${1:?usage: verify-natives.sh <platform> <dir> [fastddsjava.java]}
DIR=${2:?usage: verify-natives.sh <platform> <dir> [fastddsjava.java]}
JAVA_SRC=${3:-}
EXPECT_SECURITY=${EXPECT_SECURITY:-1}
JNI_ALLOW_MISSING=${JNI_ALLOW_MISSING:-}
READELF=${READELF:-readelf}
command -v "$READELF" > /dev/null || READELF=llvm-readelf

FAIL=0
err() { echo "  ERROR: $*"; FAIL=1; }
warn() { echo "  warning: $*"; }

# version_gt A B: true if dotted version A > B (numeric components; no sort -V, which busybox lacks)
version_gt() {
   local IFS=.
   local -a a b
   read -r -a a <<< "$1"
   read -r -a b <<< "$2"
   local i n=${#a[@]}
   [ ${#b[@]} -gt "$n" ] && n=${#b[@]}
   for ((i = 0; i < n; i++)); do
      local x=${a[i]:-0} y=${b[i]:-0}
      if ((10#$x > 10#$y)); then return 0; fi
      if ((10#$x < 10#$y)); then return 1; fi
   done
   return 1
}

KIND=glibc
case "$PLATFORM" in
   linux-x86_64)        CLASS=ELF64; MACHINE='X86-64' ;;
   linux-arm64)         CLASS=ELF64; MACHINE='AArch64' ;;
   linux-armhf)         CLASS=ELF32; MACHINE='ARM';     FLOAT='hard-float' ;;
   linux-riscv64)       CLASS=ELF64; MACHINE='RISC-V';  FLOAT='double-float' ;;
   linux-musl-x86_64)   CLASS=ELF64; MACHINE='X86-64';  KIND=musl ;;
   linux-musl-arm64)    CLASS=ELF64; MACHINE='AArch64'; KIND=musl ;;
   android-arm64-v8a)   CLASS=ELF64; MACHINE='AArch64'; KIND=android; ALIGN16K=1 ;;
   android-armeabi-v7a) CLASS=ELF32; MACHINE='ARM';     KIND=android; FLOAT='soft-float' ;;
   android-x86_64)      CLASS=ELF64; MACHINE='X86-64';  KIND=android; ALIGN16K=1 ;;
   *) echo "verify-natives.sh: unknown platform $PLATFORM" >&2; exit 2 ;;
esac
FLOAT=${FLOAT:-}
ALIGN16K=${ALIGN16K:-0}
# Ubuntu 20.04 baseline (glibc 2.31, libstdc++ of gcc 9/10); overridable for host builds (scripts/cppbuild.bash).
MAX_GLIBC=${MAX_GLIBC:-2.31}
MAX_GLIBCXX=${MAX_GLIBCXX:-3.4.28}
MAX_CXXABI=${MAX_CXXABI:-1.3.12}
JNI_LIB=libjnifastddsjava.so
JNI_PREFIX=Java_us_ihmc_fastddsjava_natives_fastddsjava_
OPENSSL_SYMBOLS='^(SSL|SSL_CTX|EVP|OPENSSL|OSSL|ERR|X509|X509V3|BIO|CRYPTO|ENGINE|PEM|PKCS[0-9]*|RSA|DSA|DH|EC|ECDSA|ECDH|BN|ASN1|HMAC|RAND|TLS|DTLS|OBJ|CONF|UI|i2d|d2i)_'

[ -d "$DIR" ] || { echo "verify-natives.sh: $DIR is not a directory" >&2; exit 2; }
echo "== verifying $PLATFORM natives in $DIR"

# ------------------------------------------------------------------------------------------------ file set
shopt -s nullglob
LIBS=()
for f in "$DIR"/*; do
   name=${f##*/}
   case "$name" in
      *.so | *.so.*) LIBS+=("$name") ;;
      natives.list) [ "$KIND" = android ] && err "natives.list does not belong in an Android jniLibs directory" ;;
      *) err "unexpected file $name (stale or foreign file in the platform directory)" ;;
   esac
done
shopt -u nullglob
has_lib() { local l; for l in "${LIBS[@]}"; do [ "$l" = "$1" ] && return 0; done; return 1; }
# shellcheck disable=SC2254  # $1 is a glob pattern
count_glob() { local n=0 l; for l in "${LIBS[@]}"; do case "$l" in $1) n=$((n + 1)) ;; esac; done; echo $n; }

has_lib "$JNI_LIB" || err "missing $JNI_LIB"
if [ "$KIND" = android ]; then
   for l in libc++_shared.so libfastcdr.so libfastdds.so; do has_lib "$l" || err "missing $l"; done
   [ ${#LIBS[@]} -eq 4 ] || err "expected exactly 4 libraries, found ${#LIBS[@]}: ${LIBS[*]}"
else
   [ "$(count_glob 'libfastcdr.so*')" = 1 ] || err "expected exactly one libfastcdr.so*"
   [ "$(count_glob 'libfastdds.so*')" = 1 ] || err "expected exactly one libfastdds.so*"
   [ ${#LIBS[@]} -eq 3 ] || err "expected exactly 3 libraries, found ${#LIBS[@]}: ${LIBS[*]}"
fi

needed_of() { "$READELF" -dW "$1" 2> /dev/null | sed -n 's/.*(NEEDED).*\[\(.*\)\].*/\1/p'; }
soname_of() { "$READELF" -dW "$1" 2> /dev/null | sed -n 's/.*(SONAME).*\[\(.*\)\].*/\1/p'; }

# ------------------------------------------------------------------------------------------------ natives.list
if [ "$KIND" != android ]; then
   if [ ! -f "$DIR/natives.list" ]; then
      err "missing natives.list"
   else
      # Same rules as the Java loader (fastddsjavaNativeLibrary.parseManifest): a UTF-8 BOM on the first line is
      # dropped, every line is trimmed, blank lines and lines starting with '#' are ignored.
      LIST=()
      first=1
      while IFS= read -r line || [ -n "$line" ]; do
         if [ $first = 1 ]; then line=${line#$'\xef\xbb\xbf'}; first=0; fi
         line=${line#"${line%%[![:space:]]*}"}
         line=${line%"${line##*[![:space:]]}"}
         case "$line" in '' | '#'*) continue ;; esac
         LIST+=("$line")
      done < "$DIR/natives.list"
      echo "  natives.list: ${LIST[*]:-}"
      if [ ${#LIST[@]} -eq 0 ]; then
         err "natives.list lists no libraries"
         LIST=()
      else
         [ ${#LIST[@]} -eq ${#LIBS[@]} ] || err "natives.list has ${#LIST[@]} entries, the directory has ${#LIBS[@]} libraries"
         [ "${LIST[${#LIST[@]} - 1]}" = "$JNI_LIB" ] || err "natives.list must end with $JNI_LIB"
      fi
      seen=" "
      for l in ${LIST[@]+"${LIST[@]}"}; do
         # The loader rejects anything but a plain file name.
         case "$l" in */* | *\\* | *:* | .*) err "natives.list: '$l' is not a plain file name"; continue ;; esac
         [ -f "$DIR/$l" ] || { err "natives.list names missing file $l"; continue; }
         case "$seen" in *" $l "*) err "natives.list lists $l twice" ;; esac
         for n in $(needed_of "$DIR/$l"); do
            if has_lib "$n"; then
               case "$seen" in *" $n "*) ;; *) err "natives.list loads $l before its dependency $n" ;; esac
            fi
         done
         seen="$seen$l "
      done
   fi
fi

# ------------------------------------------------------------------------------------------------ per library
system_lib_ok() {
   case "$KIND:$1" in
      glibc:libc.so.6 | glibc:libm.so.6 | glibc:libdl.so.2 | glibc:librt.so.1 | glibc:libpthread.so.0) return 0 ;;
      glibc:libstdc++.so.6 | glibc:libgcc_s.so.1 | glibc:ld-linux-*.so.* | glibc:ld-linux.so.*) return 0 ;;
      musl:libc.musl-*.so.1 | musl:libstdc++.so.6 | musl:libgcc_s.so.1) return 0 ;;
      android:libc.so | android:libm.so | android:libdl.so | android:liblog.so) return 0 ;;
   esac
   return 1
}

# Defined, exported dynamic symbols (one name per line, version suffix removed).
exported_symbols() {
   "$READELF" --dyn-syms -W "$1" | awk '
      $1 ~ /^[0-9]+:$/ && ($5 == "GLOBAL" || $5 == "WEAK" || $5 == "UNIQUE") && ($6 == "DEFAULT" || $6 == "PROTECTED") {
         for (i = 7; i <= NF; i++) if ($i ~ /^(UND|ABS|COM|[0-9]+)$/) break
         if (i < NF && $i != "UND") { n = $(i + 1); sub(/@.*/, "", n); print n }
      }'
}
undefined_symbols() {
   "$READELF" --dyn-syms -W "$1" | awk '
      $1 ~ /^[0-9]+:$/ { for (i = 7; i <= NF; i++) if ($i ~ /^(UND|ABS|COM|[0-9]+)$/) break
                         if (i < NF && $i == "UND") { n = $(i + 1); sub(/@.*/, "", n); print n } }'
}
max_version() { # prefix file -> highest <prefix>_x.y.z required by the file (version needs)
   local best="" v
   for v in $("$READELF" -V "$2" | sed -n "s/.*Name: $1_\([0-9][0-9.]*\).*/\1/p"); do
      if [ -z "$best" ] || version_gt "$v" "$best"; then best=$v; fi
   done
   echo "$best"
}

printf '  %-24s %10s %8s %-10s %-10s %s\n' file bytes exports align max-glibc runpath
for lib in "${LIBS[@]}"; do
   f="$DIR/$lib"
   hdr=$("$READELF" -hW "$f" 2> /dev/null) || { err "$lib is not an ELF file"; continue; }
   echo "$hdr" | grep -Eq "Class:[[:space:]]+$CLASS" || err "$lib: wrong ELF class (expected $CLASS)"
   echo "$hdr" | grep -Eq "(Machine|Arch):.*$MACHINE" || err "$lib: wrong machine (expected $MACHINE): $(echo "$hdr" | grep -E 'Machine' | sed 's/^ *//')"
   echo "$hdr" | grep -Eq 'Type:[[:space:]]+DYN' || err "$lib: not a shared object"
   if [ -n "$FLOAT" ]; then
      echo "$hdr" | grep -Eq "Flags:.*$FLOAT" || err "$lib: wrong float ABI (expected $FLOAT)"
   fi

   sections=$("$READELF" -SW "$f")
   if echo "$sections" | grep -Eq '[[:space:]]\.(symtab|debug_[a-z_]+)[[:space:]]'; then err "$lib: not stripped"; fi

   phdrs=$("$READELF" -lW "$f")
   stack=$(echo "$phdrs" | awk '$1 == "GNU_STACK" { f = ""; for (i = 7; i < NF; i++) f = f $i; print f }')
   if [ -z "$stack" ]; then err "$lib: no PT_GNU_STACK (executable stack by default)"
   elif [[ "$stack" == *E* ]]; then err "$lib: requests an executable stack"; fi
   aligns=$(echo "$phdrs" | awk '$1 == "LOAD" { print $NF }' | sort -u | tr '\n' ' ')
   if [ "$ALIGN16K" = 1 ]; then
      for a in $aligns; do
         [ $((a)) -ge 16384 ] || err "$lib: LOAD segment aligned to $a, Android 16 KB page devices need >= 0x4000"
      done
   fi

   dyn=$("$READELF" -dW "$f")
   if echo "$dyn" | grep -Eq '\(TEXTREL\)|\(FLAGS\).*TEXTREL'; then err "$lib: has text relocations"; fi
   runpath=$(echo "$dyn" | sed -n 's/.*(RUNPATH).*\[\(.*\)\].*/\1/p')
   rpath=$(echo "$dyn" | sed -n 's/.*(RPATH).*\[\(.*\)\].*/\1/p')
   [ -z "$rpath" ] || err "$lib: DT_RPATH [$rpath] (expected none; use RUNPATH)"
   if [ "$KIND" = android ]; then
      [ -z "$runpath" ] || err "$lib: RUNPATH [$runpath] on Android (leaks a build path; bionic loads by SONAME)"
      [ "$(soname_of "$f")" = "$lib" ] || err "$lib: SONAME '$(soname_of "$f")' differs from the file name"
   else
      [ -z "$runpath" ] || [ "$runpath" = '$ORIGIN' ] || err "$lib: RUNPATH [$runpath], only \$ORIGIN is allowed"
      case "$lib" in
         libfastdds.so* | "$JNI_LIB")
            [ "$runpath" = '$ORIGIN' ] || err "$lib: RUNPATH must be \$ORIGIN (dependencies are found next to it)" ;;
      esac
      case "$lib" in
         libfastcdr.so* | libfastdds.so*)
            [ "$(soname_of "$f")" = "$lib" ] || err "$lib: SONAME '$(soname_of "$f")' differs from the file name" ;;
      esac
   fi

   for n in $(needed_of "$f"); do
      if ! has_lib "$n" && ! system_lib_ok "$n"; then err "$lib: NEEDED $n is neither shipped nor an allowed system library"; fi
   done

   undef=$(undefined_symbols "$f")
   atomics=$(echo "$undef" | grep -c '^__atomic_' || true)
   [ "$atomics" = 0 ] || err "$lib: $atomics undefined __atomic_* symbols (would need libatomic at runtime): $(echo "$undef" | grep '^__atomic_' | head -3 | tr '\n' ' ')"
   # OpenSSL must be linked in statically: an unresolved OpenSSL symbol means it was not, and the library would only
   # load in a process that happens to provide a compatible OpenSSL.
   nussl=$(echo "$undef" | grep -Ec "$OPENSSL_SYMBOLS" || true)
   [ "$nussl" = 0 ] || err "$lib: $nussl undefined OpenSSL symbols, e.g. $(echo "$undef" | grep -E "$OPENSSL_SYMBOLS" | head -3 | tr '\n' ' ')"

   exports=$(exported_symbols "$f")
   nexp=$(echo "$exports" | grep -c . || true)
   nssl=$(echo "$exports" | grep -Ec "$OPENSSL_SYMBOLS" || true)
   [ "$nssl" = 0 ] || err "$lib: exports $nssl OpenSSL symbols, e.g. $(echo "$exports" | grep -E "$OPENSSL_SYMBOLS" | head -3 | tr '\n' ' ')"

   glibc="-"
   if [ "$KIND" = glibc ]; then
      if "$READELF" -V "$f" | grep -q 'Name: GLIBC_PRIVATE'; then err "$lib: uses GLIBC_PRIVATE symbols"; fi
      glibc=$(max_version GLIBC "$f")
      if [ -n "$glibc" ] && version_gt "$glibc" "$MAX_GLIBC"; then err "$lib: needs GLIBC_$glibc > $MAX_GLIBC"; fi
      v=$(max_version GLIBCXX "$f")
      if [ -n "$v" ] && version_gt "$v" "$MAX_GLIBCXX"; then err "$lib: needs GLIBCXX_$v > $MAX_GLIBCXX"; fi
      v=$(max_version CXXABI "$f")
      if [ -n "$v" ] && version_gt "$v" "$MAX_CXXABI"; then err "$lib: needs CXXABI_$v > $MAX_CXXABI"; fi
      glibc=${glibc:--}
   fi
   printf '  %-24s %10s %8s %-10s %-10s %s\n' "$lib" "$(wc -c < "$f" | tr -d ' ')" "$nexp" "${aligns% }" "$glibc" "${runpath:--}"
done

# ------------------------------------------------------------------------------------------------ libfastdds
fastdds=""
fastcdr=""
for l in ${LIBS[@]+"${LIBS[@]}"}; do
   case "$l" in
      libfastdds.so*) fastdds=$DIR/$l ;;
      libfastcdr.so*) fastcdr=$DIR/$l ;;
   esac
done
# A library that does not NEED its dependency still links when undefined symbols are allowed, but then cannot be
# loaded (Android's linker does not resolve symbols from an earlier, separately loaded library).
needs() { # lib dependency-lib
   local dep list
   dep=$(soname_of "$2")
   [ -n "$dep" ] || dep=${2##*/}
   list=$'\n'"$(needed_of "$1")"$'\n'
   case "$list" in *$'\n'"$dep"$'\n'*) ;; *) err "${1##*/}: does not link ${2##*/} (no NEEDED $dep)" ;; esac
}
if [ -n "$fastdds" ] && [ -n "$fastcdr" ]; then needs "$fastdds" "$fastcdr"; fi
if [ -n "$fastdds" ] && has_lib "$JNI_LIB"; then needs "$DIR/$JNI_LIB" "$fastdds"; fi
if [ -n "$fastdds" ]; then
   for n in $(needed_of "$fastdds"); do
      case "$n" in *ssl* | *crypto*) err "${fastdds##*/}: dynamic OpenSSL dependency $n" ;; esac
   done
   if [ "$EXPECT_SECURITY" = 1 ]; then
      # The builtin plugin names exist only in a SECURITY=ON build (SecurityPluginFactory).
      if grep -q 'builtin.PKI-DH' "$fastdds" && grep -q 'builtin.AES-GCM-GMAC' "$fastdds"; then
         echo "  ${fastdds##*/}: DDS-Security built in"
      else
         err "${fastdds##*/}: built without DDS-Security (no builtin.PKI-DH / builtin.AES-GCM-GMAC)"
      fi
   fi
fi

# ------------------------------------------------------------------------------------------------ JNI exports
if has_lib "$JNI_LIB"; then
   jni_exports=$(exported_symbols "$DIR/$JNI_LIB")
   echo "$jni_exports" | grep -qx 'JNI_OnLoad' || err "$JNI_LIB: JNI_OnLoad is not exported"
   njava=$(echo "$jni_exports" | grep -c "^$JNI_PREFIX" || true)
   nother=$(echo "$jni_exports" | grep -vEc "^(Java_|JNI_On)" || true)
   echo "  $JNI_LIB: $njava Java_* exports, $nother other exports"
   [ "$njava" -gt 0 ] || err "$JNI_LIB: no ${JNI_PREFIX}* exports"
   if [ -n "$JAVA_SRC" ]; then
      if [ ! -f "$JAVA_SRC" ]; then
         err "Java source $JAVA_SRC not found"
      else
         # Declared native methods: every 'native <type> <name>(' outside comment lines (several per line are found;
         # the declaration must not span lines). '_' is mangled to '_1'.
         methods=$( { grep -vE '^[[:space:]]*(\*|//|/\*)' "$JAVA_SRC" \
                      | grep -oE '(^|[^A-Za-z0-9_$])native[[:space:]][^;(){}=]*\(' \
                      | sed -E 's/[[:space:]]*\($//; s/.*[^A-Za-z0-9_$]//' | sort -u; } || true)
         ndecl=0
         missing=()
         for m in $methods; do
            ndecl=$((ndecl + 1))
            mangled=${m//_/_1}
            if ! echo "$jni_exports" | grep -Eq "^$JNI_PREFIX${mangled}(__.*)?$"; then
               case " $JNI_ALLOW_MISSING " in
                  *" $m "*) warn "$JNI_LIB: does not implement native $m (allowed by JNI_ALLOW_MISSING)" ;;
                  *) missing+=("$m") ;;
               esac
            fi
         done
         echo "  $JNI_LIB: $ndecl native methods declared in ${JAVA_SRC##*/}"
         [ "$ndecl" -gt 0 ] || err "no native methods found in $JAVA_SRC"
         [ ${#missing[@]} -eq 0 ] || err "$JNI_LIB: missing JNI implementations for: ${missing[*]}"
         for s in $(echo "$jni_exports" | grep "^$JNI_PREFIX" | sed "s/^$JNI_PREFIX//; s/__.*//"); do
            m=${s//_1/_}
            echo "$methods" | grep -qx "$m" || warn "$JNI_LIB: exports $JNI_PREFIX$s, which fastddsjava.java does not declare"
         done
      fi
   fi
fi

if [ $FAIL -ne 0 ]; then
   echo "== $PLATFORM: VERIFICATION FAILED"
   exit 1
fi
echo "== $PLATFORM: verification OK"
