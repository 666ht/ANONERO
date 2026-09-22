#!/usr/bin/env bash
# ANONERO native library builder

set -euo pipefail
export SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1704067200}"
export ZERO_AR_DATE=1
export TZ=UTC
export LC_ALL="${LC_ALL:-C.UTF-8}"

if [ "${1:-}" = fetch ]; then TARGET=${TARGET:-android64}; fi
: "${TARGET:?set TARGET to one of: android64 android32 linux}"

CMAKE_VERSION=3.31.12
CMAKE_SHA256=0dc2e9a6860f06bf10bd8fadc03e35d9eeb4df46e33763a7e480e987758f385c
NDK_REVISION=r27c
NDK_SHA1=090e8083a715fdb1a3e402d0763c388abb03fb4e
BOOST_VERSION=1.90.0
BOOST_SHA256=e848446c6fec62d8a96b44ed7352238b3de040b8b9facd4d6963b32f541e00f5
ICONV_VERSION=1.15
ICONV_SHA256=ccf536620a45458d26ba83887a983b96827001e92a13847b45e4925cc8913178
ZLIB_VERSION=1.3.1
ZLIB_SHA256=9a93b2b7dfdac77ceba5a558a580e74667dd6fede4585b91eefb60f03b72df23
OPENSSL_VERSION=3.6.2
OPENSSL_SHA256=aaf51a1fe064384f811daeaeb4ec4dce7340ec8bd893027eee676af31e83a04f
ZMQ_VERSION=v4.3.4
ZMQ_COMMIT=4097855ddaaa65ed7b5e8cb86d143842a594eebd
SODIUM_VERSION=1.0.22
SODIUM_SHA256=adbdd8f16149e81ac6078a03aca6fc03b592b89ef7b5ed83841c086191be3349
EXPAT_VERSION=2.7.5
EXPAT_TAG=R_2_7_5
EXPAT_SHA256=9931f9860d18e6cf72d183eb8f309bfb96196c00e1d40caa978e95bc9aa978b6
UNBOUND_VERSION=1.24.2
UNBOUND_SHA256=44e7b53e008a6dcaec03032769a212b46ab5c23c105284aa05a4f3af78e59cdb
POLYSEED_COMMIT=b7c35bb3c6b91e481ecb04fc235eaff69c507fa1
UTF8PROC_COMMIT=1cb28a66ca79a0845e99433fd1056257456cef8b

case "$TARGET" in
android64) PLATFORM=android; ABI=arm64-v8a; API=21; CLANG=aarch64-linux-android21; HOST=aarch64-linux-android; OPENSSL_TARGET=android-arm64; MONERO_TARGET=release-static-android-armv8 ;;
android32) PLATFORM=android; ABI=armeabi-v7a; API=21; CLANG=armv7a-linux-androideabi21; HOST=arm-linux-androideabi; OPENSSL_TARGET=android-arm; MONERO_TARGET=release-static-android-armv7 ;;
linux) PLATFORM=linux; ABI=linux-x86_64; HOST=; OPENSSL_TARGET=linux-x86_64; MONERO_ARCH=x86-64 ;;
*) echo "unknown TARGET '$TARGET' (android64 android32 linux)" >&2; exit 1 ;;
esac

WORK=${WORK:-/opt/build}
NDK=${NDK:-/opt/android-ndk-$NDK_REVISION}
TC_DIR=$NDK/toolchains/llvm/prebuilt/linux-x86_64
if [ "$PLATFORM" = android ]; then PREFIX=$TC_DIR/sysroot/usr; else PREFIX=${PREFIX:-$WORK/prefix}; fi
OUT=${OUT:-/opt/out}
SRC=${SRC:-$WORK/src}
MONERO=${MONERO:-/src}
NPROC=${NPROC:-$(nproc)}
mkdir -p "$WORK" "$PREFIX" "$SRC"

android() { [ "$PLATFORM" = android ]; }
HOSTF=(); if [ -n "$HOST" ]; then HOSTF=(--host="$HOST"); fi

grab() {
  cd "$SRC"
  [ -f "$2" ] || curl -fL --http1.1 --retry 8 --retry-all-errors --retry-delay 3 -C - -o "$2" "$1"
  echo "$3  $2" | sha256sum -c
}
get() { grab "$@"; cd "$WORK"; }
pin() {
  cd "$WORK"
  if [ ! -d "$3" ]; then
    i=1; until git clone "$1" -b "$2" "$3"; do
      [ "$i" -ge 5 ] && { echo "clone of $1 failed after 5 tries" >&2; return 1; }
      i=$((i+1)); sleep 3; rm -rf "$3"
    done
  fi
  cd "$3"; git reset --hard "$4"; test "$(git rev-parse HEAD)" = "$4"
}
cc() {
  if android; then
    export ANDROID_NDK_ROOT=$NDK TC=$TC_DIR
    export CC="ccache $CLANG-clang" CXX="ccache $CLANG-clang++"
    export CCACHE_DIR="${CCACHE_DIR:-/ccache}"
    export CCACHE_MAXSIZE="${CCACHE_MAXSIZE:-5G}"
    export AR=$TC/bin/llvm-ar RANLIB=$TC/bin/llvm-ranlib STRIP=$TC/bin/llvm-strip
    export PATH=$TC/bin:$PATH
  fi
}

step_fetch() {
  grab "https://github.com/boostorg/boost/releases/download/boost-$BOOST_VERSION/boost-$BOOST_VERSION-b2-nodocs.tar.gz" "boost-$BOOST_VERSION.tar.gz" "$BOOST_SHA256"
  grab "http://ftp.gnu.org/pub/gnu/libiconv/libiconv-$ICONV_VERSION.tar.gz" "libiconv-$ICONV_VERSION.tar.gz" "$ICONV_SHA256"
  grab "https://github.com/madler/zlib/releases/download/v$ZLIB_VERSION/zlib-$ZLIB_VERSION.tar.gz" "zlib-$ZLIB_VERSION.tar.gz" "$ZLIB_SHA256"
  grab "http://www.openssl.org/source/openssl-$OPENSSL_VERSION.tar.gz" "openssl-$OPENSSL_VERSION.tar.gz" "$OPENSSL_SHA256"
  grab "https://download.libsodium.org/libsodium/releases/libsodium-$SODIUM_VERSION.tar.gz" "libsodium-$SODIUM_VERSION.tar.gz" "$SODIUM_SHA256"
  grab "https://github.com/libexpat/libexpat/releases/download/$EXPAT_TAG/expat-$EXPAT_VERSION.tar.gz" "expat-$EXPAT_VERSION.tar.gz" "$EXPAT_SHA256"
  grab "https://www.nlnetlabs.nl/downloads/unbound/unbound-$UNBOUND_VERSION.tar.gz" "unbound-$UNBOUND_VERSION.tar.gz" "$UNBOUND_SHA256"
  echo "fetched $(ls "$SRC" | wc -l) sources"
}

step_toolchain() {
  cd /usr
  curl -fL --http1.1 --retry 8 --retry-all-errors --retry-delay 3 -C - -O "https://github.com/Kitware/CMake/releases/download/v$CMAKE_VERSION/cmake-$CMAKE_VERSION-linux-x86_64.tar.gz"
  echo "$CMAKE_SHA256  cmake-$CMAKE_VERSION-linux-x86_64.tar.gz" | sha256sum -c
  tar -xzf "cmake-$CMAKE_VERSION-linux-x86_64.tar.gz" && rm -f "cmake-$CMAKE_VERSION-linux-x86_64.tar.gz"
  ln -sf "/usr/cmake-$CMAKE_VERSION-linux-x86_64/bin/"* /usr/local/bin/
  android || return 0
  cd /opt
  curl -fL --http1.1 --retry 8 --retry-all-errors --retry-delay 3 -C - -O "https://dl.google.com/android/repository/android-ndk-$NDK_REVISION-linux.zip"
  echo "$NDK_SHA1  android-ndk-$NDK_REVISION-linux.zip" | sha1sum -c
  unzip -q "android-ndk-$NDK_REVISION-linux.zip" && rm -f "android-ndk-$NDK_REVISION-linux.zip"
}

dep_iconv() { if ! android; then return 0; fi; cc; get "http://ftp.gnu.org/pub/gnu/libiconv/libiconv-$ICONV_VERSION.tar.gz" "libiconv-$ICONV_VERSION.tar.gz" "$ICONV_SHA256"; cd "$WORK/libiconv-$ICONV_VERSION"; ./configure --build=x86_64-linux-gnu "${HOSTF[@]}" --prefix="$PREFIX" --disable-rpath --enable-static --disable-shared; make -j"$NPROC" && make install; }
dep_boost() { cc; get "https://github.com/boostorg/boost/releases/download/boost-$BOOST_VERSION/boost-$BOOST_VERSION-b2-nodocs.tar.gz" "boost-$BOOST_VERSION.tar.gz" "$BOOST_SHA256"; cd "$WORK/boost-$BOOST_VERSION"; ./bootstrap.sh --prefix="$PREFIX" --without-icu; B2=(--build-type=minimal link=static cflags=-fPIC cxxflags=-fPIC --with-chrono --with-date_time --with-filesystem --with-program_options --with-regex --with-serialization --with-system --with-thread --with-locale threading=multi); if android; then echo "using clang : android : ${CXX} : <compileflags>--sysroot=${TC}/sysroot <compileflags>-I${PREFIX}/include <linkflags>--sysroot=${TC}/sysroot <linkflags>-L${PREFIX}/lib <linkflags>-liconv <archiver>${AR} <ranlib>${RANLIB} ;" > user-config.jam; ./b2 "${B2[@]}" --user-config=user-config.jam runtime-link=static --build-dir=android --stagedir=android toolset=clang-android threadapi=pthread target-os=android "-sICONV_PATH=$PREFIX" install -j"$NPROC"; else ./b2 "${B2[@]}" install -j"$NPROC"; fi; ar rcsD "$PREFIX/lib/libboost_system.a"; }
dep_zlib() { cc; get "https://github.com/madler/zlib/releases/download/v$ZLIB_VERSION/zlib-$ZLIB_VERSION.tar.gz" "zlib-$ZLIB_VERSION.tar.gz" "$ZLIB_SHA256"; rm -rf "$WORK/zlib"; mv "$WORK/zlib-$ZLIB_VERSION" "$WORK/zlib"; cd "$WORK/zlib"; if android; then ./configure --static && make -j"$NPROC"; else CFLAGS=-fPIC ./configure --static --prefix="$PREFIX" && make -j"$NPROC" && make install; fi; }
dep_openssl() { cc; get "https://www.openssl.org/source/old/3.6/openssl-$OPENSSL_VERSION.tar.gz" "openssl-$OPENSSL_VERSION.tar.gz" "$OPENSSL_SHA256"; cd "$WORK/openssl-$OPENSSL_VERSION"; if android; then ANDROID_NDK_ROOT=$NDK ./Configure "$OPENSSL_TARGET" "-D__ANDROID_API__=$API" -fPIC -static no-shared no-tests --with-zlib-include="$WORK/zlib/include" --with-zlib-lib="$WORK/zlib/lib" --prefix="$PREFIX" --openssldir="$PREFIX"; else ./Configure "$OPENSSL_TARGET" -fPIC no-shared no-tests --with-zlib-include="$PREFIX/include" --with-zlib-lib="$PREFIX/lib" --prefix="$PREFIX" --openssldir="$PREFIX" --libdir=lib; fi; make -j"$NPROC" && make install_sw; }
dep_zmq() { cc; pin https://github.com/zeromq/libzmq.git "$ZMQ_VERSION" libzmq "$ZMQ_COMMIT"; ./autogen.sh; ZMQF=(); android || ZMQF=(--disable-Werror); ./configure --prefix="$PREFIX" "${HOSTF[@]}" --enable-static --disable-shared --with-pic --without-libsodium --disable-curve --without-documentation "${ZMQF[@]}"; make -j"$NPROC" && make install; }
dep_sodium() { cc; get "https://download.libsodium.org/libsodium/releases/libsodium-$SODIUM_VERSION.tar.gz" "libsodium-$SODIUM_VERSION.tar.gz" "$SODIUM_SHA256"; cd "$WORK/libsodium-$SODIUM_VERSION"; ./autogen.sh; ./configure --prefix="$PREFIX" "${HOSTF[@]}" --enable-static --disable-shared --with-pic; make -j"$NPROC" && make install; }
dep_expat() { cc; get "https://github.com/libexpat/libexpat/releases/download/$EXPAT_TAG/expat-$EXPAT_VERSION.tar.gz" "expat-$EXPAT_VERSION.tar.gz" "$EXPAT_SHA256"; cd "$WORK/expat-$EXPAT_VERSION"; ./configure --prefix="$PREFIX" "${HOSTF[@]}" --enable-static --disable-shared --with-pic; make -j"$NPROC" && make install; }
dep_unbound() { cc; get "https://www.nlnetlabs.nl/downloads/unbound/unbound-$UNBOUND_VERSION.tar.gz" "unbound-$UNBOUND_VERSION.tar.gz" "$UNBOUND_SHA256"; cd "$WORK/unbound-$UNBOUND_VERSION"; ./configure --prefix="$PREFIX" "${HOSTF[@]}" --enable-static --disable-shared --with-pic --disable-flto --with-ssl="$PREFIX" --with-libexpat="$PREFIX"; make -j"$NPROC" && make install; }

cmakeflags() { if android; then echo "-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-$API"; else echo "-DCMAKE_POSITION_INDEPENDENT_CODE=ON -DCMAKE_BUILD_TYPE=Release"; fi; }
dep_polyseed() { cc; pin https://github.com/tevador/polyseed.git master polyseed "$POLYSEED_COMMIT"; cmake -DCMAKE_INSTALL_PREFIX="$PREFIX" $(cmakeflags) . && make -j"$NPROC" && make install; }
dep_utf8proc() { cc; pin https://github.com/JuliaStrings/utf8proc v2.8.0 utf8proc "$UTF8PROC_COMMIT"; mkdir -p build && cd build && rm -rf ../CMakeCache.txt ../CMakeFiles/; cmake -DCMAKE_INSTALL_PREFIX="$PREFIX" $(cmakeflags) .. && make -j"$NPROC" && make install; }

step_monero() {
  cc
  if [ ! -s "$WORK/polyseed/include/polyseed.h" ]; then
    echo "MISSING $WORK/polyseed/include/polyseed.h" >&2
    exit 1
  fi
  rm -rf "$MONERO/external/polyseed" "$MONERO/external/utf8proc"
  mkdir -p "$MONERO/external"
  cp -a "$WORK/polyseed" "$MONERO/external/polyseed"
  cp -a "$WORK/utf8proc" "$MONERO/external/utf8proc"

  sed -i \
    -e '/check_submodule(external\/rapidjson)/d' \
    -e '/check_submodule(external\/trezor-common)/d' \
    -e '/check_submodule(external\/randomx)/d' \
    -e '/check_submodule(external\/supercop)/d' \
    -e '/check_submodule(external\/polyseed)/d' \
    -e '/check_submodule(external\/utf8proc)/d' \
    "$MONERO/CMakeLists.txt"

  if android; then
    python3 - "$MONERO/CMakeLists.txt" "$MONERO/translations/CMakeLists.txt" <<'PY'
from pathlib import Path
import re
import shutil
import sys
top = Path(sys.argv[1])
trans = Path(sys.argv[2])
s = top.read_text()
pattern = re.compile(r'\n?\s*include\(ExternalProject\)\s*\n\s*ExternalProject_Add\(generate_translations_header.*?\n\s*include_directories\("\$\{CMAKE_CURRENT_BINARY_DIR\}/translations"\)', re.S)
replacement = '\ninclude_directories("${CMAKE_CURRENT_BINARY_DIR}/translations")'
s2, n = pattern.subn(replacement, s, count=1)
if n != 1:
    raise SystemExit("failed to remove generate_translations_header ExternalProject block")
top.write_text(s2)
t = trans.read_text()
marker = "project(translations)\n"
guard = 'if(CMAKE_SYSTEM_NAME STREQUAL "Android")\n  return()\nendif()\n'
if guard not in t:
    if marker not in t:
        raise SystemExit("translations project marker not found")
    t = t.replace(marker, marker + "\n" + guard, 1)
trans.write_text(t)
shutil.rmtree(top / "build", ignore_errors=True)
PY
  fi

  if android; then
    python3 - "$MONERO/Makefile" <<'PY'
from pathlib import Path
import re
import sys
p = Path(sys.argv[1])
s = p.read_text()
for target in ("release-static-android-armv7", "release-static-android-armv8"):
    pat = re.compile(rf"({re.escape(target)}:\n)(.*?)(?=\nrelease-static-|\nfuzz:)", re.S)
    m = pat.search(s)
    if not m:
        raise SystemExit(f"missing {target} target")
    body = m.group(2)
    body = "\n".join(line for line in body.split("\n") if "release/translations" not in line)
    body = "\n".join(line for line in body.split("\n") if line.strip())
    body = "\t" + "\n\t".join(["mkdir -p $(builddir)/release"] + [line.lstrip("\t") for line in body.split("\n")]) + "\n"
    s = s[:m.start(2)] + body + s[m.end(2):]
p.write_text(s)
PY
  fi

  rm -rf "$MONERO/src/wallet/polyseed"
  ln -s "$MONERO/polyseed" "$MONERO/src/wallet/polyseed"
  cd "$MONERO"
  git config --global --add safe.directory /src
  git config --global --add safe.directory "$MONERO"
  if android; then
    # The Monero Makefile's Android release target changes into build/release
    # before invoking CMake. We removed the stale CMake tree above, so recreate
    # the directory explicitly rather than letting the target assume it exists.
    mkdir -p "$MONERO/build/release"
    CFLAGS="-I$MONERO ${CFLAGS:-}" CXXFLAGS="-I$MONERO ${CXXFLAGS:-}" \
      CMAKE_INCLUDE_PATH="$PREFIX/include" \
      CMAKE_LIBRARY_PATH="$PREFIX/lib" ANDROID_STANDALONE_TOOLCHAIN_PATH= \
      ANDROID_NDK_ROOT="$NDK" USE_SINGLE_BUILDDIR=1 \
      make "$MONERO_TARGET" -j"$NPROC"
    cmake --build "$MONERO/build/release" --target wallet_api --parallel "$NPROC"
    test -s "$MONERO/build/release/lib/libwallet_api.a"
  else
    mkdir -p build/release && cd build/release
    cmake -D CMAKE_BUILD_TYPE=Release -D STATIC=OFF -D ARCH="$MONERO_ARCH" -D BUILD_64=ON -D BUILD_TESTS=OFF -D BUILD_GUI_DEPS=1 -D USE_DEVICE_TREZOR=OFF -D STACK_TRACE=OFF -D CMAKE_POSITION_INDEPENDENT_CODE=ON -D BUILD_TAG="linux-x64" -D CMAKE_PREFIX_PATH="$PREFIX" -D BOOST_ROOT="$PREFIX" -D BOOST_IGNORE_SYSTEM_PATHS=ON -D OPENSSL_ROOT_DIR="$PREFIX" ../..
    make wallet_api -j"$NPROC"
  fi
  cd "$MONERO/build/release" && mkdir -p lib
  find . -path ./lib -prune -o -name '*.a' -exec cp '{}' lib \;
}

step_collect() {
  mkdir -p "$OUT/monero"
  find "$PREFIX/lib" -maxdepth 1 -name '*.a' -exec cp -a {} "$OUT"/ \; 2>/dev/null || true
  cp -a "$MONERO"/build/release/lib/. "$OUT"/monero/
  local missing=0 l
  for l in libwallet_api.a libwallet.a libcryptonote_core.a libcryptonote_basic.a libringct.a libcncrypto.a libcommon.a libepee.a libdevice.a libmnemonics.a libpolyseed_wrapper.a libeasylogging.a liblmdb.a libblocks.a libcheckpoints.a libmultisig.a libversion.a libnet.a librandomx.a libhardforks.a librpc_base.a libblockchain_db.a libringct_basic.a libcryptonote_format_utils_basic.a; do
    [ -s "$OUT/monero/$l" ] || { echo "MISSING monero/$l" >&2; missing=1; }
  done
  [ -s "$OUT/libpolyseed.a" ] || { echo "MISSING libpolyseed.a" >&2; missing=1; }
  [ "$missing" -eq 0 ] || { echo "incomplete build for $TARGET" >&2; exit 1; }
  echo "OK $TARGET ($ABI): $(find "$OUT" -name '*.a' | wc -l) archives"
}

case "${1:-}" in
  fetch) step_fetch ;;
  toolchain) step_toolchain ;;
  monero) step_monero ;;
  collect) step_collect ;;
  "") echo "usage: TARGET=<t> $0 {fetch|toolchain|<dep>|monero|collect}" >&2; exit 1 ;;
  *) declare -F "dep_$1" >/dev/null || { echo "unknown step '$1'" >&2; exit 1; }; "dep_$1" ;;
esac
