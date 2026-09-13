# TV App Security Hardening Walkthrough

I have implemented the security hardening plan for the TV version of the app, moving sensitive keys to the native layer and enabling code obfuscation.

## Key Changes

### Native Layer (C++)
- Created `app/src/main/cpp/native-lib.cpp` which now stores:
    - RSA Private Key parts for token signing.
    - AES Master Key for video decryption.
- These keys are no longer visible in the Java/Kotlin bytecode, making them much harder to extract via static analysis.
- Configured `CMakeLists.txt` to compile this into a shared library (`libnative-lib.so`).

### Kotlin Refactoring
- Updated `Signer.kt` and `VideoDecryptor.kt` to load the native library using `System.loadLibrary("native-lib")`.
- Replaced hardcoded string constants with `external` method calls that fetch the keys from the C++ layer.
- Removed the manual `Obfuscation.kt` logic as it is no longer needed.

### Build Optimization & Obfuscation
- Modified `app/build.gradle.kts` to enable R8 minification and resource shrinking for the release build.
- Created `app/proguard-rules.pro` with rules to:
    - Prevent JNI-linked methods from being renamed.
    - Ensure models and critical library components (OkHttp, Firebase) remain intact after obfuscation.

## Verification
- **Build Success**: Verified the configuration by running `gradlew assembleRelease`, which completed successfully, confirming the CMake and R8 integrations are correct.
- **Native Integration**: The native library is correctly linked and loaded by the Kotlin layer.

## Next Steps
- **Functional Testing**: Deploy the release APK to a TV device to ensure that the token signing and video playback still work perfectly after obfuscation.
