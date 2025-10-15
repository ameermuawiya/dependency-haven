# Ignore XML parser warnings
-dontwarn org.kxml2.**
-dontwarn org.xmlpull.**

# Keep XML parser classes
-keep class org.kxml2.** { *; }
-keep class org.xmlpull.** { *; }
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.v1.** { *; }