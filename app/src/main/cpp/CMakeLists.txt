cmake_minimum_required(VERSION 3.22.1)
project(oxygen_inference LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

option(OXYGEN_ENABLE_LLAMA "Fetch and link llama.cpp for on-device inference" ON)
set(OXYGEN_LLAMA_TAG "b5210" CACHE STRING "llama.cpp git tag")
set(OXYGEN_LLAMA_SOURCE_DIR "" CACHE PATH "Optional local llama.cpp source directory")

add_library(oxygen_inference SHARED
    oxygen_jni.cpp
    oxygen_inference.cpp
    gguf_reader.cpp
)

target_include_directories(oxygen_inference PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(oxygen_inference
    ${log-lib}
    ${android-lib}
)

if(OXYGEN_ENABLE_LLAMA)
    if(OXYGEN_LLAMA_SOURCE_DIR AND EXISTS "${OXYGEN_LLAMA_SOURCE_DIR}/include/llama.h")
        message(STATUS "OXYGEN: using local llama.cpp source at ${OXYGEN_LLAMA_SOURCE_DIR}")
        add_subdirectory("${OXYGEN_LLAMA_SOURCE_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp")
        set(OXYGEN_LLAMA_LOCAL_TARGET TRUE)
    else()
        include(FetchContent)
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
    set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
    set(GGML_CUDA OFF CACHE BOOL "" FORCE)
    set(GGML_METAL OFF CACHE BOOL "" FORCE)
    set(GGML_BLAS OFF CACHE BOOL "" FORCE)
    set(GGML_CPU ON CACHE BOOL "" FORCE)

        FetchContent_Declare(
            llama
            URL https://github.com/ggml-org/llama.cpp/archive/refs/tags/${OXYGEN_LLAMA_TAG}.tar.gz
        )
        FetchContent_MakeAvailable(llama)
    endif()

    if(TARGET llama)
        target_link_libraries(oxygen_inference llama)
        target_compile_definitions(oxygen_inference PRIVATE OXYGEN_HAS_LLAMA=1)
    elseif(TARGET llama.cpp)
        target_link_libraries(oxygen_inference llama.cpp)
        target_compile_definitions(oxygen_inference PRIVATE OXYGEN_HAS_LLAMA=1)
    else()
        message(WARNING "llama.cpp target not found after FetchContent")
    endif()
    if(TARGET ggml)
        target_link_libraries(oxygen_inference ggml)
    endif()
else()
    target_compile_definitions(oxygen_inference PRIVATE OXYGEN_HAS_LLAMA=0)
endif()

target_compile_options(oxygen_inference PRIVATE
    -fvisibility=hidden
    -Wall
    -Wextra
    -Wno-unused-parameter
)
