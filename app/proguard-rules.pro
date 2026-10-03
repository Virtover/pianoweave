# Retrofit API Interface & Gson Models
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Keep Gson library internals
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Keep Retrofit API interface
-keep interface com.lumenchord.pianoweave.api.PianoApi { *; }

# Keep API data classes and their field names un-obfuscated for Gson
-keep class com.lumenchord.pianoweave.api.** {
    <fields>;
    <init>();
    ** *;
}

# Keep WorkManager Background Worker
-keep class com.lumenchord.pianoweave.worker.TranscriptionWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
