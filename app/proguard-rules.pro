# Keep PJSIP JNI bridge
-keep class org.pjsip.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep our SIP service
-keep class com.ipdial.service.** { *; }
-keep class com.ipdial.data.model.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Compose
-dontwarn androidx.compose.**

# Fix for Ripple crash in Compose 1.7+ with Material 2/3
-keep class androidx.compose.material.ripple.** { *; }
-keep class androidx.compose.material3.ripple.** { *; }
-keep interface androidx.compose.foundation.IndicationNodeFactory { *; }

# More aggressive shrinking
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
