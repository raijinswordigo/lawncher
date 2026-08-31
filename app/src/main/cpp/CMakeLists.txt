# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.
cmake_minimum_required(VERSION 3.22.1)

project("lawncher")

file(GLOB_RECURSE KIWI_SOURCES
        "${CMAKE_CURRENT_SOURCE_DIR}/api/kiwi/*.c"
)

add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        # Core
        core/crasher.c
        core/hook.c
        core/java.c
        core/32patch.c
        core/physicspatch.c
        core/stdstring.c
        core/assets.c
        core/core_ffi.c
        core/components.c
        core/jpatch.c
        core/map.c
        core/toml.c

        lawncher/jni/filerift_jni.cpp
        lawncher/platform/pod_gl_renderer.cpp
        lawncher/platform/pod_render.cpp
        lawncher/platform/pvr_loader.cpp
        lawncher/platform/pvrtc_decoder.cpp
        lawncher/tools/boulder.cpp
        lawncher/tools/filerift.cpp
        lawncher/tools/pod_loader.cpp
        lawncher/tools/scene_schemas.cpp

        # Caver
        caver/caver.c
        caver/main/Scene.c
        caver/main/GameController.c
        caver/main/SceneObject.c

        # Lua
        lua/lua.c
        lua/lauxlib.c

        # Lua APIs
        api/apis.c
        api/fs.c
        api/console.c
        api/cstrings.c
        api/jstuff/main.c
        api/jstuff/button.c
        api/jstuff/drawer.c
        api/jstuff/hooks.c
        api/jstuff/overlay.c
        api/jstuff/seekbar.c
        api/jstuff/textinput.c
        api/jstuff/legacy_bc.c
        api/jstuff/lni.c
        api/exclusives/memory.c
        api/lua_libs/lua_libs.c
        ${KIWI_SOURCES}

        # Events
        events/scene.c

        # Entrypoint
        main.c
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/lua
        ${CMAKE_CURRENT_SOURCE_DIR}/core
        ${CMAKE_CURRENT_SOURCE_DIR}/libs
        ${CMAKE_CURRENT_SOURCE_DIR}/caver
        ${CMAKE_CURRENT_SOURCE_DIR}/lawncher
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../GlossHook/GlossHook/include
)

add_library(GlossHook SHARED IMPORTED)
set_target_properties(
        GlossHook
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libGlossHook.so
)

add_library(ffi STATIC IMPORTED)
set_target_properties(
        ffi
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/third_party/libffi/${ANDROID_ABI}/lib/libffi.a
)
target_include_directories(${CMAKE_PROJECT_NAME}
        PUBLIC ${CMAKE_SOURCE_DIR}/third_party/libffi/${ANDROID_ABI}/include
)

# Gloss, Android and LOGD marco
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        GLESv3
        EGL
        z
        GlossHook
        ffi
        android
        log)