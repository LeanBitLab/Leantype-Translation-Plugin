# Proguard rules for LeanType Translation Plugin

-keep class helium314.keyboard.translation.plugin.TranslationProviderImpl {
    public <init>();
    public <methods>;
}

-keep class helium314.keyboard.translation.plugin.MlKitTranslatorBridge {
    public <init>(android.content.Context);
    public <methods>;
}

-keep interface helium314.keyboard.latin.translation.ITranslationProvider {
    <methods>;
}

-keep interface helium314.keyboard.latin.translation.TranslationModelDownloadListener {
    <methods>;
}

-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepnames class com.google.mlkit.** extends androidx.work.ListenableWorker

-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }
-dontwarn com.google.**
-dontwarn androidx.**
