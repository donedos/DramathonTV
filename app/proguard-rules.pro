# Keep native methods to prevent JNI breaks
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Signer and VideoDecryptor because they have native methods
-keep class com.dramathon.dramathonplus.sign.Signer { *; }
-keep class com.dramathon.dramathonplus.decrypt.VideoDecryptor { *; }

# Keep model-like classes in client package if they are used for JSON parsing
-keep class com.dramathon.dramathonplus.client.** { *; }

# General OkHttp / Okio rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
