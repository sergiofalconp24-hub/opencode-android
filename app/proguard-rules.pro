# Keep NanoHTTPD annotations/reflection points
-dontwarn org.eclipse.jetty.**
-keep class fi.iki.elonen.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room / KSP generated
-keep class * extends androidx.room.RoomDatabase { *; }