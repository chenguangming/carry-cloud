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
-keep class **_FragmentFinder { *; }
-keep class androidx.fragment.app.* { *; }
-keep class androidx.arch.core.internal.* { *; }

-keep class ch.qos.** { *; }
-keep class org.slf4j.** { *; }
-keepattributes *Annotation

-keep class com.qmuiteam.qmui.arch.record.RecordIdClassMap { *; }
-keep class com.qmuiteam.qmui.arch.scheme.SchemeMap {*;}

-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}

-dontwarn javax.mail.**