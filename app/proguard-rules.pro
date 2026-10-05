# OpenBrows ProGuard rules
-keep class com.openbrows.app.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
