cmake_minimum_required(VERSION 3.0)

set(PROJECT_NAME "vncserver-lib")

set(CMAKE_FIND_ROOT_PATH ${CMAKE_INSTALL_PREFIX})

# MUST be done before call to 'project'
# See https://cmake.org/pipermail/cmake/2018-January/067002.html
get_cmake_property(vars CACHE_VARIABLES)
foreach(var ${vars})
    get_property(currentHelpString CACHE "${var}" PROPERTY HELPSTRING)
    if("${currentHelpString}" MATCHES "No help, variable specified on the command line." OR "${currentHelpString}" STREQUAL "")
        # message("${var} = [${${var}}]  --  ${currentHelpString}") # uncomment to see the variables being processed
        list(APPEND CL_ARGS "-D${var}=${${var}}")
    endif()
endforeach()

# Build the dependencies first

include(ExternalProject)

ExternalProject_Add(project-libjpeg-turbo
    SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo
    CMAKE_ARGS ${CL_ARGS} -DENABLE_SHARED=OFF
    BUILD_BYPRODUCTS ${CMAKE_INSTALL_PREFIX}/lib/libturbojpeg.a
)
add_library(jpegturbo STATIC IMPORTED)
set_property(TARGET jpegturbo PROPERTY
    IMPORTED_LOCATION ${CMAKE_INSTALL_PREFIX}/lib/libturbojpeg.a
)

ExternalProject_Add(project-libpng
    SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libpng
    CMAKE_ARGS ${CL_ARGS} -DPNG_SHARED=OFF -DPNG_TESTS=OFF -DCMAKE_FIND_ROOT_PATH=${CMAKE_INSTALL_PREFIX}
    BUILD_BYPRODUCTS ${CMAKE_INSTALL_PREFIX}/lib/libpng.a
)
add_library(png STATIC IMPORTED)
set_property(TARGET png PROPERTY
    IMPORTED_LOCATION ${CMAKE_INSTALL_PREFIX}/lib/libpng.a
)

# Build the native library

project(${PROJECT_NAME})

add_library(vncserver SHARED
    vncjni.cpp
    vncserver.cpp
    libvncserver/libvncserver/main.c
    libvncserver/libvncserver/rfbserver.c
    libvncserver/libvncserver/rfbregion.c
    libvncserver/libvncserver/auth.c
    libvncserver/libvncserver/sockets.c
    libvncserver/libvncserver/stats.c
    libvncserver/libvncserver/corre.c
    libvncserver/libvncserver/hextile.c
    libvncserver/libvncserver/rre.c
    libvncserver/libvncserver/translate.c
    libvncserver/libvncserver/cutpaste.c
    libvncserver/libvncserver/httpd.c
    libvncserver/libvncserver/cursor.c
    libvncserver/libvncserver/font.c
    libvncserver/libvncserver/draw.c
    libvncserver/libvncserver/selbox.c
    libvncserver/libvncserver/cargs.c
    libvncserver/libvncserver/ultra.c
    libvncserver/libvncserver/scale.c
    libvncserver/libvncserver/zlib.c
    libvncserver/libvncserver/zrle.c
    libvncserver/libvncserver/zrleoutstream.c
    libvncserver/libvncserver/zrlepalettehelper.c
    libvncserver/libvncserver/tight.c
    libvncserver/common/d3des.c
    libvncserver/common/vncauth.c
    libvncserver/common/minilzo.c
    libvncserver/common/zywrletemplate.c
    libvncserver/common/turbojpeg.c
)

add_dependencies(vncserver project-libpng project-libjpeg-turbo)

include_directories(
    ${CMAKE_INSTALL_PREFIX}/include
    libvncserver/
    libvncserver//common
    libvncserver/libvncserver
)

target_link_libraries(vncserver png jpegturbo z log)

