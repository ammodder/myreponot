# AREENAX native app — R8 / ProGuard rules (release minify enabled)

# ---- General ---------------------------------------------------------------
-keepattributes *Annotation*
-keepattributes InnerClasses,Signature,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# ---- kotlinx.serialization -------------------------------------------------
-keep,includedescriptorclasses class com.areenax.app.**$$serializer { *; }
-keepclassmembers class com.areenax.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.areenax.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# All DTO models (com.areenax.nativeapp.data.**) keep their members so the
# generated serializers keep working under obfuscation.
-keepclassmembers class com.areenax.app.data.** { *; }

# ---- Retrofit --------------------------------------------------------------
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ---- OkHttp / Okio ---------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- ZXing (embedded scanner) — consumer rules ship with the library; these
#      are belt-and-braces so CaptureActivity is never stripped.
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# ---- Coil ------------------------------------------------------------------
-dontwarn coil.**
