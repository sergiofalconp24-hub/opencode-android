# Keep Gson models
-keepattributes Signature
-keep class dev.opencode.android.data.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**