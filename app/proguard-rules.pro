# Created by: Ameer Muawiya
# Optimized ProGuard rules for io.thorenkoder.android

# ---------------------------
# Android & Kotlin Core
# ---------------------------
-keep class androidx.** { *; }
-dontwarn androidx.**
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ---------------------------
# XML Parsing
# ---------------------------
-dontwarn org.kxml2.**
-dontwarn org.xmlpull.**
-keep class org.kxml2.** { *; }
-keep class org.xmlpull.** { *; }
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.v1.** { *; }

# ---------------------------
# JSON
# ---------------------------
-keep class org.json.** { *; }
-dontwarn org.json.**

# ---------------------------
# App Specific Activities
# ---------------------------
-keep class io.thorenkoder.android.** { *; }

# ---------------------------
# FileProvider & Reflection
# ---------------------------
-keep class androidx.core.content.FileProvider { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ---------------------------
# Logging & Debug
# ---------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ---------------------------
# Remove Unused Code
# ---------------------------
-dontnote
-dontwarn
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
