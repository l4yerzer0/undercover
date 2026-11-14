cmake_minimum_required(VERSION 3.22.1)

project(byedpi_native)

# ByeDPI core (https://github.com/hufrea/byedpi, MIT) is vendored under byedpi/.
# win_service.c is Windows-only and is not vendored.
file(GLOB BYE_DPI_SRC byedpi/*.c)

add_library(byedpi SHARED ${BYE_DPI_SRC} native-lib.c)
target_include_directories(byedpi PRIVATE byedpi)

target_compile_options(byedpi PRIVATE
        -std=c99 -O2 -Wall -Wno-unused -Wextra -Wno-unused-parameter -pedantic)
target_compile_definitions(byedpi PRIVATE ANDROID_APP)

target_link_libraries(byedpi PRIVATE android log)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
