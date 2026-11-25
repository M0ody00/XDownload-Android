# Preserve application APIs exercised by instrumentation and Android component entry points.
-keep class com.xdownload.mobile.** { *; }
# Keep native/extractor APIs and media APIs used directly by instrumentation.
-keep class com.yausername.** { *; }
-keep class androidx.media3.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-dontobfuscate
