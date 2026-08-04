# R8 rules for the release build. Most of what this app touches reflectively lives in libraries
# that ship consumer rules of their own (WorkManager, Play Billing, Play Services Ads, DataStore),
# so this file only carries the app-level keeps and the diagnostics settings.

# Keep line numbers and remap them through mapping.txt, so a Play Console crash report still points
# at a real source line. Without SourceFile the frames come back as "Unknown Source".
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WorkManager instantiates workers by class name from the enqueued request, so the reminder worker
# cannot be renamed even though nothing calls its constructor directly.
-keep class com.algora.app.core.notify.** extends androidx.work.ListenableWorker { *; }

# Room builds its generated <Name>_Impl subclass reflectively by name, so R8 sees no caller for the
# no-arg constructor and strips it. WorkManager's internal WorkDatabase goes through exactly that
# path, which crashed the shrunk build at startup in androidx.startup.InitializationProvider.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Compose's runtime uses these annotations to decide what is restartable; stripping them changes
# recomposition behaviour rather than just shrinking the binary.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# play-core-ktx references a GMS-internal nullness annotation that is not on the runtime classpath.
# It is a compile-time marker with no behaviour, so the reference going unresolved is harmless.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite

# Strip verbose/debug logging from the shipped binary. Errors and warnings stay.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
