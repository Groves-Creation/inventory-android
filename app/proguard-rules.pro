-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keep class dev.convex.** { *; }

# Convex's generated Android bindings call JNA through native methods and reflection.
# JNA's peer fields must retain their original names in minified release builds.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# JNA also contains desktop-only AWT integration that Android never loads.
-dontwarn java.awt.**

# The Brother print SDK resolves printer models, settings and native helpers reflectively,
# so its classes must survive minification intact.
-keep class com.brother.** { *; }
-dontwarn com.brother.**

# ML Kit builds its component graph from manifest metadata and reflective registrars.
# Keep the graph and the code-scanner implementation intact so R8 cannot remove a
# component (notably SharedPrefManager) that is only reached through that registry.
-keep class com.google.firebase.components.** { *; }
-keep class com.google.mlkit.common.** { *; }
-keep class com.google.mlkit.vision.codescanner.** { *; }
-keep class com.google.android.gms.internal.mlkit_code_scanner.** { *; }
