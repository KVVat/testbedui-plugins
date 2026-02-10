# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.

project("acvp_kernel_harness"
        VERSION 1.0
        DESCRIPTION "acvp kernel harness port"
        LANGUAGES C)

find_library( log-lib log )
add_executable(acvp_kernel_harness
        main.c
        message.c
        util.c
        alg_aead.c
        alg_cipher.c
        alg_common.c
        alg_drbg.c
        alg_hash.c)

target_link_libraries(acvp_kernel_harness ${log-lib})

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

