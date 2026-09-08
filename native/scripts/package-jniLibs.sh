#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PREFIX_ROOT="${ROOT}/native/out/prefix"
DEST="${ROOT}/app/src/main/jniLibs"
HEADERS="${ROOT}/native/prebuilt/include"
NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

host_tag() {
  case "$(uname -s)" in
    Darwin) echo "darwin-x86_64" ;;
    *) echo "linux-x86_64" ;;
  esac
}

# The NDK sysroot uses the plain "arm-linux-androideabi" directory for 32-bit ARM,
# not the "armv7a-" clang prefix, so libc++_shared.so lives under this name.
sysroot_triple() {
  case "$1" in
    arm64-v8a) echo "aarch64-linux-android" ;;
    armeabi-v7a) echo "arm-linux-androideabi" ;;
    x86_64) echo "x86_64-linux-android" ;;
    *) echo "unsupported ABI $1" >&2; return 1 ;;
  esac
}

mkdir -p "${DEST}" "${HEADERS}"
for ABI_DIR in "${PREFIX_ROOT}"/*; do
  [[ -d "${ABI_DIR}" ]] || continue
  ABI="$(basename "${ABI_DIR}")"
  mkdir -p "${DEST}/${ABI}"
  if [[ -d "${ABI_DIR}/lib" ]]; then
    find "${ABI_DIR}/lib" -name "*.so" -exec cp -f {} "${DEST}/${ABI}/" \;
  fi
  if [[ -d "${ABI_DIR}/include" ]]; then
    cp -R "${ABI_DIR}/include/." "${HEADERS}/"
  fi
  if [[ -n "${NDK_HOME}" ]]; then
    TRIPLE="$(sysroot_triple "${ABI}")"
    CXX_SHARED="${NDK_HOME}/toolchains/llvm/prebuilt/$(host_tag)/sysroot/usr/lib/${TRIPLE}/libc++_shared.so"
    if [[ -f "${CXX_SHARED}" ]]; then
      cp -f "${CXX_SHARED}" "${DEST}/${ABI}/"
    else
      # FFmpeg links libass/HarfBuzz, so the C++ runtime must ship with it.
      echo "libc++_shared.so not found for ${ABI} at ${CXX_SHARED}" >&2
      exit 1
    fi
  fi
done
echo "Copied shared libraries into ${DEST}"
echo "Copied FFmpeg headers into ${HEADERS}"
