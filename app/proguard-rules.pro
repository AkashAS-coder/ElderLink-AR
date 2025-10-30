# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ML Kit Pose Detection rules
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep native methods for ML Kit
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve annotations for ML Kit
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# 16KB page size compatibility
-keep class com.google.mlkit.vision.** { *; }
-keep class com.google.android.gms.vision.** { *; }