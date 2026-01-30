# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep data classes
-keepclassmembers class com.monitor.alert.data.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Huawei HMS Push
-keep class com.huawei.hms.** { *; }
-keep class com.huawei.agconnect.** { *; }
-dontwarn com.huawei.hms.**
-dontwarn com.huawei.agconnect.**
-dontwarn org.bouncycastle.crypto.BlockCipher
-dontwarn org.bouncycastle.crypto.engines.AESEngine
-dontwarn org.bouncycastle.crypto.prng.SP800SecureRandom
-dontwarn org.bouncycastle.crypto.prng.SP800SecureRandomBuilder

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep service classes
-keep class com.monitor.alert.service.** { *; }
-keep class com.monitor.alert.push.** { *; }
-keep class com.monitor.alert.receiver.** { *; }

# Keep WorkManager workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# Keep ViewModel constructors
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
