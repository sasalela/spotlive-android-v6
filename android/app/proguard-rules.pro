-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class net.spotlive.player.ponte.PonteJavascript { *; }
