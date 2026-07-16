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

##保持 native 方法不被混淆
#-keepclasseswithmembernames class * {
#  native <methods>;
#}
#
##保持自定义控件类不被混淆
#-keepclasseswithmembers class * {
#  public <init>(android.content.Context, android.util.AttributeSet);
#}
#
##保持自定义控件类不被混淆
#-keepclassmembers class * extends android.app.Activity {
#  public void *(android.view.View);
#}

-keep class app.aaps.pump.aaruy.** { *; }
-keep interface app.aaps.pump.aaruy.** { *; }
-keepattributes Exceptions, InnerClasses, Signature, Deprecated, SourceFile, LineNumberTable, *Annotation*, EnclosingMethod