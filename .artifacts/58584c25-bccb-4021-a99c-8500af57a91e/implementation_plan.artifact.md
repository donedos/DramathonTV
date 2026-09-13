# Implementation Plan - TV App Security Hardening

This plan outlines the steps to harden the TV application against reverse engineering and service blocking by enabling code obfuscation and moving sensitive keys to native storage.

## User Review Required

> [!IMPORTANT]
> **R8 Obfuscation**: Enabling R8 can sometimes cause runtime crashes if certain classes (like those used for JSON serialization or Reflection) are renamed. I will add standard keep rules for OkHttp and Firebase, but we may need to refine them if issues arise during testing.
> **NDK Installation**: This plan assumes the Android NDK is installed in your SDK environment. If not, you will need to install it via the SDK Manager.

## Proposed Changes

### Build Configuration

#### [MODIFY] [build.gradle.kts](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/build.gradle.kts)
- Enable `isMinifyEnabled` and `isShrinkResources` in the release build type.
- Configure `externalNativeBuild` with CMake to support JNI.

#### [NEW] [CMakeLists.txt](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/src/main/cpp/CMakeLists.txt)
- Define the native library and its source files.

### Native Layer (JNI)

#### [NEW] [native-lib.cpp](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/src/main/cpp/native-lib.cpp)
- Store the RSA private key parts and AES Master Key.
- Implement JNI methods to safely return these keys to the Kotlin layer.

### Kotlin Layer Refactoring

#### [MODIFY] [Signer.kt](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/src/main/java/com/dramathon/dramathonplus/sign/Signer.kt)
- Load the native library using `System.loadLibrary("native-lib")`.
- Replace hardcoded key parts with calls to `external` native functions.

#### [MODIFY] [VideoDecryptor.kt](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/src/main/java/com/dramathon/dramathonplus/decrypt/VideoDecryptor.kt)
- Replace the hardcoded `MASTER_KEY` with a call to a native function.

#### [DELETE] [Obfuscation.kt](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/src/main/java/com/dramathon/dramathonplus/sign/Obfuscation.kt)
- Remove the weak Kotlin-based obfuscation as it will be superseded by the native implementation.

### R8 Rules

#### [NEW] [proguard-rules.pro](file:///Users/zed/AndroidStudioProjects/DramaThonTV/app/proguard-rules.pro)
- Add keep rules for:
    - Native method names (to prevent them from being renamed, which breaks JNI).
    - Model classes used for JSON parsing.

---

## Verification Plan

### Automated Tests
- Execute `gradlew assembleRelease` to verify the build process with R8 and CMake.

### Manual Verification
- Deploy the release build to a TV emulator or device.
- Verify that the app can still successfully fetch dramas and decrypt video samples.
- Verify that focus handling (sidebar, grid) still works correctly after obfuscation.
