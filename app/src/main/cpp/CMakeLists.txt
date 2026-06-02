cmake_minimum_required(VERSION 3.22)
project("metrolist_discord")

if(DISCORD_BRIDGE)
    # GMS builds: build the Discord bridge native library
    add_library(metrolist_discord SHARED
        ${CMAKE_CURRENT_SOURCE_DIR}/../../gms/cpp/discord_bridge.cpp
    )

    target_include_directories(metrolist_discord PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/../../gms/cpp/include
    )

    add_library(discord_partner_sdk SHARED IMPORTED)
    set_target_properties(discord_partner_sdk PROPERTIES
        IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/../../gms/jniLibs/${ANDROID_ABI}/libdiscord_partner_sdk.so
    )

    target_link_libraries(metrolist_discord
        discord_partner_sdk
        jnigraphics
        log
    )

    target_compile_features(metrolist_discord PUBLIC cxx_std_17)
else()
    # FOSS/Izzy builds: build an empty stub library
    add_library(metrolist_discord SHARED
        ${CMAKE_CURRENT_SOURCE_DIR}/discord_stub.cpp
    )

    target_compile_features(metrolist_discord PUBLIC cxx_std_17)
endif()
