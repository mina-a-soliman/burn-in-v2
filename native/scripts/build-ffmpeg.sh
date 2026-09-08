#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCK="${ROOT}/third_party/ffmpeg.lock"
OUT="${ROOT}/native/out"
JNI_LIBS="${ROOT}/app/src/main/jniLibs"

source_lock() {
  while IFS='=' read -r key value; do
    [[ -z "${key}" || "${key}" =~ ^# ]] && continue
    printf -v "${key}" '%s' "${value}"
  done < "${LOCK}"
}

source_lock

NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "${NDK_HOME}" ]]; then
  echo "ANDROID_NDK_HOME is required" >&2
  exit 1
fi

API=26
JOBS="$(nproc)"
mkdir -p "${OUT}/src" "${OUT}/prefix"

clone_at() {
  local url="$1"
  local ref="$2"
  local dest="$3"
  if [[ ! -d "${dest}/.git" ]]; then
    git clone --depth 1 --branch "${ref}" "${url}" "${dest}" || {
      git clone "${url}" "${dest}"
      git -C "${dest}" checkout "${ref}"
    }
  fi
}

clone_at "${freetype_url}" "${freetype_ref}" "${OUT}/src/freetype"
clone_at "${harfbuzz_url}" "${harfbuzz_ref}" "${OUT}/src/harfbuzz"
clone_at "${fribidi_url}" "${fribidi_ref}" "${OUT}/src/fribidi"
clone_at "${libass_url}" "${libass_ref}" "${OUT}/src/libass"
clone_at "${x264_url}" "${x264_ref}" "${OUT}/src/x264"
clone_at "${ffmpeg_url}" "${ffmpeg_ref}" "${OUT}/src/ffmpeg"

IFS=',' read -r -a ABI_LIST <<< "${abis}"

host_tag() {
  case "$(uname -s)" in
    Darwin) echo "darwin-x86_64" ;;
    *) echo "linux-x86_64" ;;
  esac
}

abi_triple() {
  case "$1" in
    arm64-v8a) echo "aarch64-linux-android" ;;
    armeabi-v7a) echo "armv7a-linux-androideabi" ;;
    x86_64) echo "x86_64-linux-android" ;;
    *) echo "unsupported ABI $1" >&2; return 1 ;;
  esac
}

# config.sub rejects NDK's armv7a-* clang prefix; autotools still uses this --host.
autotools_host() {
  case "$1" in
    armeabi-v7a) echo "arm-linux-androideabi" ;;
    *) abi_triple "$1" ;;
  esac
}

TOOLCHAIN="${NDK_HOME}/toolchains/llvm/prebuilt/$(host_tag)"
SYSROOT="${TOOLCHAIN}/sysroot"
export PATH="${TOOLCHAIN}/bin:${PATH}"

# FriBidi gen.tab (and similar) must compile and run on the build machine.
export CC_FOR_BUILD="${CC_FOR_BUILD:-cc}"
export CXX_FOR_BUILD="${CXX_FOR_BUILD:-c++}"
export CFLAGS_FOR_BUILD="${CFLAGS_FOR_BUILD:--O2}"
export LDFLAGS_FOR_BUILD="${LDFLAGS_FOR_BUILD:-}"
BUILD_TRIPLE="$("${CC_FOR_BUILD}" -dumpmachine)"

write_meson_cross() {
  local abi="$1"
  local triple="$2"
  local prefix="$3"
  local cpu_family cpu
  case "${abi}" in
    arm64-v8a) cpu_family="aarch64"; cpu="aarch64" ;;
    armeabi-v7a) cpu_family="arm"; cpu="armv7" ;;
    *) cpu_family="x86_64"; cpu="x86_64" ;;
  esac
  local cross="${OUT}/meson-android-${abi}.ini"
  cat > "${cross}" <<EOF
[binaries]
c = '${CC}'
cpp = '${CXX}'
ar = '${AR}'
strip = '${STRIP}'
pkg-config = 'pkg-config'

[built-in options]
c_args = ['-fPIC', '-O2']
cpp_args = ['-fPIC', '-O2']
c_link_args = ['-fPIC', '-Wl,-z,max-page-size=16384']
cpp_link_args = ['-fPIC', '-Wl,-z,max-page-size=16384']
prefix = '${prefix}'

[host_machine]
system = 'android'
cpu_family = '${cpu_family}'
cpu = '${cpu}'
endian = 'little'
EOF
  echo "${cross}"
}

for ABI in "${ABI_LIST[@]}"; do
  TRIPLE="$(abi_triple "${ABI}")"
  HOST="$(autotools_host "${ABI}")"
  PREFIX="${OUT}/prefix/${ABI}"
  mkdir -p "${PREFIX}"
  export CC="${TOOLCHAIN}/bin/${TRIPLE}${API}-clang"
  export CXX="${TOOLCHAIN}/bin/${TRIPLE}${API}-clang++"
  export AR="${TOOLCHAIN}/bin/llvm-ar"
  export RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
  export STRIP="${TOOLCHAIN}/bin/llvm-strip"
  export NM="${TOOLCHAIN}/bin/llvm-nm"
  export PKG_CONFIG_PATH="${PREFIX}/lib/pkgconfig"
  export PKG_CONFIG_LIBDIR="${PREFIX}/lib/pkgconfig"
  unset PKG_CONFIG_SYSROOT_DIR || true
  export CFLAGS="-fPIC -O2"
  export CXXFLAGS="-fPIC -O2"
  export LDFLAGS="-fPIC -Wl,-z,max-page-size=16384"
  MESON_CROSS="$(write_meson_cross "${ABI}" "${TRIPLE}" "${PREFIX}")"

  if [[ ! -f "${PREFIX}/lib/libfreetype.a" ]]; then
    pushd "${OUT}/src/freetype" >/dev/null
    make distclean || true
    ./autogen.sh || true
    ./configure --build="${BUILD_TRIPLE}" --host="${HOST}" --prefix="${PREFIX}" --enable-static --disable-shared --with-png=no --with-harfbuzz=no --with-bzip2=no --with-brotli=no
    make -j"${JOBS}"
    make install
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libfreetype.a"

  if [[ ! -f "${PREFIX}/lib/libfribidi.a" ]]; then
    pushd "${OUT}/src/fribidi" >/dev/null
    rm -rf "build-${ABI}"
    meson setup "build-${ABI}" \
      --cross-file "${MESON_CROSS}" \
      --prefix "${PREFIX}" \
      --default-library static \
      -Ddocs=false \
      -Dbin=false \
      -Dtests=false
    meson compile -C "build-${ABI}"
    meson install -C "build-${ABI}"
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libfribidi.a"

  # libass shapes through hb-ft, so HarfBuzz must see the FreeType we just installed.
  if [[ ! -f "${PREFIX}/lib/libharfbuzz.a" ]]; then
    pushd "${OUT}/src/harfbuzz" >/dev/null
    rm -rf "build-${ABI}"
    meson setup "build-${ABI}" \
      --cross-file "${MESON_CROSS}" \
      --prefix "${PREFIX}" \
      --default-library static \
      -Dtests=disabled \
      -Ddocs=disabled \
      -Dutilities=disabled \
      -Dfreetype=enabled \
      -Dglib=disabled \
      -Dcairo=disabled \
      -Dgobject=disabled \
      -Dicu=disabled
    meson compile -C "build-${ABI}"
    meson install -C "build-${ABI}"
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libharfbuzz.a"

  if [[ ! -f "${PREFIX}/lib/libass.a" ]]; then
    pushd "${OUT}/src/libass" >/dev/null
    make distclean || true
    ./autogen.sh
    ./configure --build="${BUILD_TRIPLE}" --host="${HOST}" --prefix="${PREFIX}" --enable-static --disable-shared --disable-require-system-font-provider
    make -j"${JOBS}"
    make install
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libass.a"

  if [[ ! -f "${PREFIX}/lib/libx264.a" ]]; then
    pushd "${OUT}/src/x264" >/dev/null
    make distclean || true
    ./configure --host="${HOST}" --prefix="${PREFIX}" --enable-static --enable-pic --disable-cli --disable-opencl --sysroot="${SYSROOT}" --cross-prefix="${TOOLCHAIN}/bin/llvm-" --extra-cflags="-fPIC" --extra-ldflags="-Wl,-z,max-page-size=16384"
    make -j"${JOBS}"
    make install
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libx264.a"

  if [[ -f "${PREFIX}/lib/libavformat.so" ]]; then
    echo "Skipping FFmpeg configure for ${ABI} (already installed)"
  else
    pushd "${OUT}/src/ffmpeg" >/dev/null
    if [[ -f ffbuild/config.mak ]]; then
      make distclean || true
    fi
    pkg-config --exists --print-errors x264
    ./configure \
      --prefix="${PREFIX}" \
      --target-os=android \
      --arch="$([[ ${ABI} == arm64-v8a ]] && echo aarch64 || echo arm)" \
      --cpu="$([[ ${ABI} == arm64-v8a ]] && echo armv8-a || echo armv7-a)" \
      --enable-cross-compile \
      --pkg-config="$(command -v pkg-config)" \
      --cc="${CC}" \
      --cxx="${CXX}" \
      --ar="${AR}" \
      --nm="${NM}" \
      --ranlib="${RANLIB}" \
      --strip="${STRIP}" \
      --sysroot="${SYSROOT}" \
      --enable-gpl \
      --enable-pic \
      --enable-libass \
      --enable-libfreetype \
      --enable-libharfbuzz \
      --enable-libfribidi \
      --enable-libx264 \
      --enable-encoder=h264_mediacodec \
      --enable-decoder=h264_mediacodec \
      --enable-jni \
      --enable-mediacodec \
      --enable-shared \
      --disable-static \
      --disable-doc \
      --disable-programs \
      --disable-debug \
      --extra-cflags="-I${PREFIX}/include -fPIC" \
      --extra-ldflags="-L${PREFIX}/lib -lm -lz -landroid -llog -Wl,-z,max-page-size=16384" \
      --extra-libs="-lc++_shared" \
      --pkg-config-flags="--static"
    make -j"${JOBS}"
    make install
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libavformat.so"
done

"${ROOT}/native/scripts/package-jniLibs.sh"
