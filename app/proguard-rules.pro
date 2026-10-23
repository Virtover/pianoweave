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

# Stored as JSON with Gson outside the api package: Drive file descriptions (read by other builds and
# devices), the cached cloud song list and pending purchases. Renamed fields made them unreadable to
# any other build, so cloud songs came back without their details.
-keep class com.lumenchord.pianoweave.cloud.CloudMetadataEnvelope { <fields>; <init>(...); }
-keep class com.lumenchord.pianoweave.cloud.CloudMidi { <fields>; <init>(...); }
-keep class com.lumenchord.pianoweave.billing.PendingPurchase { <fields>; <init>(...); }
