cmake_minimum_required(VERSION 3.22.1)
project("native-lib")

# Point to the cloned llama.cpp directory
if (EXISTS "D:/tmp/llama.cpp")
    set(LLAMA_DIR "D:/tmp/llama.cpp")
elseif (EXISTS "E:/tmp/llama.cpp")
    set(LLAMA_DIR "E:/tmp/llama.cpp")
else()
    set(LLAMA_DIR "D:/tmp/llama.cpp")
endif()

# Do not build tests or examples for llama.cpp to speed up compilation
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Build tests" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Build examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Build server" FORCE)

# Vulkan host toolchain (vulkan-host-toolchain.cmake) and GGML_VULKAN_SHADERS_GEN_TOOLCHAIN
# are passed via Gradle CMake arguments in build.gradle.kts.

# Add llama.cpp as a subdirectory
add_subdirectory(${LLAMA_DIR} llama)

# Define our JNI wrapper library
add_library(
        native-lib
        SHARED
        native-lib.cpp)

# Find Android libraries
find_library(
        log-lib
        log)

# Link our library with llama, ggml and Android log
if (GGML_VULKAN)
    target_link_libraries(
            native-lib
            llama
            ggml-vulkan
            ${log-lib})
else()
    target_link_libraries(
            native-lib
            llama
            ggml-cpu
            ${log-lib})
endif()
