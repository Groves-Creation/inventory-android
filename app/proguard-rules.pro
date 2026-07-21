-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keep class dev.convex.** { *; }

# Convex's generated Android bindings call JNA through native methods and reflection.
# JNA's peer fields must retain their original names in minified release builds.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# JNA also contains desktop-only AWT integration that Android never loads.
-dontwarn java.awt.**
