-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions

-keep class com.burnsubtitle.ffmpeg.FFmpegEngine {
    native <methods>;
    *;
}

-keep class com.burnsubtitle.ffmpeg.SubtitleBurnProcessor { *; }
-keep class com.burnsubtitle.ffmpeg.FFmpegInitializer { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class androidx.work.** { *; }
-keep class androidx.hilt.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep class * extends androidx.work.CoroutineWorker

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
