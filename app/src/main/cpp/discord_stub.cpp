// Stub native library for non-GMS builds
// Discord RPC functionality is not available without the Discord Partner SDK

#include <jni.h>

// Empty JNI_OnLoad - no-op for non-GMS builds
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    return JNI_VERSION_1_6;
}
