cmake_minimum_required(VERSION 3.22.1)
project(burnffmpeg)

add_library(burnffmpeg SHARED
    jni/ffmpeg_jni.cpp
    jni/log_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)
target_include_directories(burnffmpeg PRIVATE jni)

set(FFMPEG_PREFIX "${CMAKE_CURRENT_SOURCE_DIR}/out/prefix/${ANDROID_ABI}")
set(FFMPEG_PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt")
set(FFMPEG_INCLUDE "")
if (EXISTS "${FFMPEG_PREFIX}/include/libavformat/avformat.h")
    set(FFMPEG_INCLUDE "${FFMPEG_PREFIX}/include")
    set(FFMPEG_LIB_DIR "${FFMPEG_PREFIX}/lib")
elseif (EXISTS "${FFMPEG_PREBUILT}/include/libavformat/avformat.h")
    set(FFMPEG_INCLUDE "${FFMPEG_PREBUILT}/include")
    if (EXISTS "${FFMPEG_PREBUILT}/${ANDROID_ABI}")
        set(FFMPEG_LIB_DIR "${FFMPEG_PREBUILT}/${ANDROID_ABI}")
    endif()
endif()

if (FFMPEG_INCLUDE AND EXISTS "${FFMPEG_INCLUDE}/libavformat/avformat.h")
    message(STATUS "Linking bundled FFmpeg from ${FFMPEG_INCLUDE}")
    target_sources(burnffmpeg PRIVATE jni/ffmpeg_burn.cpp)
    target_compile_definitions(burnffmpeg PRIVATE BURN_HAVE_FFMPEG=1)
    target_include_directories(burnffmpeg PRIVATE "${FFMPEG_INCLUDE}")
    # Only the FFmpeg libraries themselves. libass, HarfBuzz, FreeType, FriBidi and x264 are
    # static archives already linked into libavfilter.so, so linking them here again would
    # duplicate their symbols inside this module.
    set(FFMPEG_SO_NAMES avfilter avformat avcodec swscale swresample avutil)
    foreach (libname IN LISTS FFMPEG_SO_NAMES)
        set(so_path "")
        if (FFMPEG_LIB_DIR AND EXISTS "${FFMPEG_LIB_DIR}/lib${libname}.so")
            set(so_path "${FFMPEG_LIB_DIR}/lib${libname}.so")
        elseif (EXISTS "${CMAKE_SOURCE_DIR}/../app/src/main/jniLibs/${ANDROID_ABI}/lib${libname}.so")
            set(so_path "${CMAKE_SOURCE_DIR}/../app/src/main/jniLibs/${ANDROID_ABI}/lib${libname}.so")
        endif()
        if (NOT so_path)
            message(FATAL_ERROR "FFmpeg headers found but lib${libname}.so is missing for ${ANDROID_ABI}")
        endif()
        add_library(${libname} SHARED IMPORTED)
        set_target_properties(${libname} PROPERTIES IMPORTED_LOCATION "${so_path}")
        target_link_libraries(burnffmpeg ${libname})
    endforeach()
else()
    message(STATUS "FFmpeg headers not found; building JNI stub. CI native build supplies the real libraries.")
endif()

target_link_libraries(burnffmpeg ${log-lib} ${android-lib})
# Required for devices with 16 KB memory pages (Android 15+ on arm64).
if (ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    target_link_options(burnffmpeg PRIVATE "-Wl,-z,max-page-size=16384")
endif()
if (ANDROID_ABI STREQUAL "armeabi-v7a")
    target_link_libraries(burnffmpeg atomic)
endif()
