set(CMAKE_BUILD_TYPE Release)
set(CMAKE_C_FLAGS -O2)
set(CMAKE_CXX_FLAGS -O2)
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE NEVER)
set(CMAKE_C_COMPILER "C:/Users/dell/scoop/apps/gcc/current/bin/gcc.exe")
set(CMAKE_CXX_COMPILER "C:/Users/dell/scoop/apps/gcc/current/bin/g++.exe")
# Skip compiler checks to avoid CMake/Ninja test compile errors on Windows
set(CMAKE_C_COMPILER_WORKS TRUE)
set(CMAKE_CXX_COMPILER_WORKS TRUE)
# Ninja must be set explicitly — not in PATH during ExternalProject host sub-build on Windows
set(CMAKE_MAKE_PROGRAM "C:/Users/dell/AppData/Local/Android/Sdk/cmake/3.22.1/bin/ninja.exe")
