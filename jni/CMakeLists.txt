#
# Compiles Opus and the KMP C shim into one static archive and, optionally, the
# JNI shared library or the standalone WebAssembly module.
#
#   cmake -S jni -B build -DBUILD_JNI=ON               desktop JVM / Android JNI
#   cmake -S jni -B build                              static archive only
#   emcmake cmake -S jni -B build-wasm -DBUILD_WASM=ON WebAssembly
#
# The source lists come from Opus's own *.mk files, the same lists its CMake
# and autotools builds use. Only the portable floating-point sources are
# compiled: no SSE/NEON/RTCD objects, so every target (including WebAssembly)
# runs one code path and cross-compilation needs no architecture assembly.
#
# OPUS_DIR points at the checkout to compile; the default is the git submodule
# next to this directory.
#
cmake_minimum_required(VERSION 3.20)

project(opus_kmp C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(BUILD_JNI "Build the JNI shared library" OFF)
option(BUILD_WASM "Build the standalone WebAssembly module" OFF)

set(OPUS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../opus" CACHE PATH "Opus checkout to build")
if(NOT EXISTS "${OPUS_DIR}/include/opus.h")
  message(FATAL_ERROR "Opus sources are missing at ${OPUS_DIR}")
endif()

# Reuse Opus's source-list reader so the file set stays in step with upstream.
include("${OPUS_DIR}/cmake/OpusFunctions.cmake")
set(OPUS_SOURCE_LISTS
  "SILK_SOURCES|${OPUS_DIR}/silk_sources.mk|silk_sources"
  "SILK_SOURCES_FLOAT|${OPUS_DIR}/silk_sources.mk|silk_sources_float"
  "CELT_SOURCES|${OPUS_DIR}/celt_sources.mk|celt_sources"
  "OPUS_SOURCES|${OPUS_DIR}/opus_sources.mk|opus_sources"
  "OPUS_SOURCES_FLOAT|${OPUS_DIR}/opus_sources.mk|opus_sources_float"
)
set(OK_OPUS_SOURCES "")
foreach(entry IN LISTS OPUS_SOURCE_LISTS)
  string(REPLACE "|" ";" fields "${entry}")
  list(GET fields 0 group)
  list(GET fields 1 file)
  list(GET fields 2 variable)
  get_opus_sources(${group} "${file}" ${variable})
  foreach(source IN LISTS ${variable})
    list(APPEND OK_OPUS_SOURCES "${OPUS_DIR}/${source}")
  endforeach()
endforeach()

# Opus stores its release in a git tag; package_version is empty in a checkout.
set(OPUS_VERSION "")
if(EXISTS "${OPUS_DIR}/.git")
  execute_process(
    COMMAND git -C "${OPUS_DIR}" describe --tags --match "v*"
    OUTPUT_VARIABLE OPUS_DESCRIBE
    OUTPUT_STRIP_TRAILING_WHITESPACE
    ERROR_QUIET
  )
  if(OPUS_DESCRIBE MATCHES "v([0-9]+\\.[0-9]+\\.[0-9]+)")
    set(OPUS_VERSION "${CMAKE_MATCH_1}")
  endif()
endif()
if(NOT OPUS_VERSION AND EXISTS "${OPUS_DIR}/package_version")
  file(STRINGS "${OPUS_DIR}/package_version" OPUS_PACKAGE_VERSION REGEX "PACKAGE_VERSION=")
  string(REGEX MATCH "([0-9]+\\.[0-9]+\\.[0-9]+)" OPUS_VERSION "${OPUS_PACKAGE_VERSION}")
endif()
if(NOT OPUS_VERSION)
  set(OPUS_VERSION "1.5.2")
endif()
message(STATUS "Building Opus ${OPUS_VERSION} from ${OPUS_DIR}")

configure_file(
  "${CMAKE_CURRENT_SOURCE_DIR}/config.h.in"
  "${CMAKE_CURRENT_BINARY_DIR}/generated/config.h"
  @ONLY
)

add_library(opus_kmp STATIC
  ${OK_OPUS_SOURCES}
  "${CMAKE_CURRENT_SOURCE_DIR}/opus_kmp.c"
)
target_include_directories(opus_kmp
  PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include"
  PRIVATE
    "${OPUS_DIR}"
    "${OPUS_DIR}/include"
    "${OPUS_DIR}/silk"
    "${OPUS_DIR}/silk/float"
    "${OPUS_DIR}/celt"
    "${CMAKE_CURRENT_BINARY_DIR}/generated"
)
target_compile_definitions(opus_kmp PRIVATE
  HAVE_CONFIG_H=1
  OPUS_BUILD=1
  VAR_ARRAYS=1
  DISABLE_DEBUG_FLOAT=1
  ENABLE_HARDENING=1
  HAVE_LRINT=1
  HAVE_LRINTF=1
)
if(CMAKE_C_COMPILER_ID MATCHES "GNU|Clang")
  target_compile_options(opus_kmp PRIVATE -Wno-unused-function -fno-strict-aliasing)
endif()

# The archive name is what the cinterop `-staticLibrary` flag and the JNI link
# line expect: libopus_kmp.a on Unix/MinGW.
set_target_properties(opus_kmp PROPERTIES OUTPUT_NAME opus_kmp)

if(NOT EMSCRIPTEN)
  # Plain `m` rather than an absolute find_library() result: during a cross
  # build (aarch64 on x86_64, MinGW on Linux) the host libm path would be
  # resolved instead of the target one.
  if(UNIX OR WIN32)
    target_link_libraries(opus_kmp PUBLIC m)
  endif()
endif()

if(BUILD_JNI)
  add_library(opus_jni SHARED "${CMAKE_CURRENT_SOURCE_DIR}/opus_jni.c")
  target_link_libraries(opus_jni PRIVATE opus_kmp)
  set_target_properties(opus_jni PROPERTIES OUTPUT_NAME opus_jni)

  if(WIN32)
    # MinGW names shared libraries lib*.dll by default; the Kotlin loader uses
    # the unprefixed opus_jni.dll. The library is pure C: only the MinGW
    # runtime itself has to be linked statically.
    set_target_properties(opus_jni PROPERTIES PREFIX "")
    target_link_options(opus_jni PRIVATE -static -static-libgcc)
  endif()

  if(ANDROID)
    # 16 KB page-size devices (Android 15+).
    target_link_options(opus_jni PRIVATE
      "-Wl,-z,max-page-size=16384"
      "-Wl,-z,common-page-size=16384"
    )
  else()
    # Derive the headers from JAVA_HOME first: FindJNI refuses to configure
    # when the target platform differs from the host (cross builds for MinGW).
    if(NOT JNI_INCLUDE_DIR AND DEFINED ENV{JAVA_HOME})
      set(JNI_INCLUDE_DIR "$ENV{JAVA_HOME}/include")
      if(WIN32)
        set(JNI_INCLUDE_DIR_PLATFORM "$ENV{JAVA_HOME}/include/win32")
      elseif(APPLE)
        set(JNI_INCLUDE_DIR_PLATFORM "$ENV{JAVA_HOME}/include/darwin")
      else()
        set(JNI_INCLUDE_DIR_PLATFORM "$ENV{JAVA_HOME}/include/linux")
      endif()
    endif()
    if(NOT JNI_INCLUDE_DIR)
      find_package(JNI REQUIRED)
    endif()
    if(JNI_INCLUDE_DIR)
      target_include_directories(opus_jni PRIVATE "${JNI_INCLUDE_DIR}")
    endif()
    if(JNI_INCLUDE_DIR_PLATFORM)
      target_include_directories(opus_jni PRIVATE "${JNI_INCLUDE_DIR_PLATFORM}")
    endif()
    if(JNI_INCLUDE_DIRS)
      target_include_directories(opus_jni PRIVATE ${JNI_INCLUDE_DIRS})
    endif()
  endif()
endif()

if(BUILD_WASM OR EMSCRIPTEN OR CMAKE_SYSTEM_NAME STREQUAL "Emscripten")
  add_executable(opus_wasm "${CMAKE_CURRENT_SOURCE_DIR}/opus_wasm_entry.c")
  target_link_libraries(opus_wasm PRIVATE opus_kmp)
  set_target_properties(opus_wasm PROPERTIES SUFFIX ".wasm")
  target_link_options(opus_wasm PRIVATE
    "-sSTANDALONE_WASM=1"
    "-sALLOW_MEMORY_GROWTH=1"
    "-sINITIAL_MEMORY=33554432"
    # Opus's encoder uses sizeable stack frames (SILK + CELT).
    "-sSTACK_SIZE=8388608"
    "-sERROR_ON_UNDEFINED_SYMBOLS=1"
    "-sEXPORTED_FUNCTIONS=_ok_version_string,_ok_strerror,_ok_encoder_create,_ok_encoder_free,_ok_encoder_reset,_ok_encoder_sample_rate,_ok_encoder_channels,_ok_encoder_encode,_ok_encoder_encode_float,_ok_encoder_ctl_int,_ok_encoder_get_int,_ok_encoder_final_range,_ok_decoder_create,_ok_decoder_free,_ok_decoder_reset,_ok_decoder_sample_rate,_ok_decoder_channels,_ok_decoder_decode,_ok_decoder_decode_float,_ok_decoder_ctl_int,_ok_decoder_get_int,_ok_decoder_final_range,_ok_decoder_get_nb_samples,_ok_packet_get_bandwidth,_ok_packet_get_samples_per_frame,_ok_packet_get_nb_channels,_ok_packet_get_nb_frames,_ok_packet_get_nb_samples,_ok_packet_has_lbrr,_ok_packet_parse,_ok_packet_pad,_ok_packet_unpad,_ok_repacketizer_create,_ok_repacketizer_free,_ok_repacketizer_reset,_ok_repacketizer_cat,_ok_repacketizer_get_nb_frames,_ok_repacketizer_out_range,_ok_repacketizer_out,_ok_pcm_soft_clip,_ok_alloc,_ok_free"
    "--no-entry"
  )
endif()
