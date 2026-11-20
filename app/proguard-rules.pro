# Add project specific ProGuard rules here.

# Preserve TensorFlow Lite native bindings and models
-keep class org.tensorflow.lite.** { *; }
-keep interface org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Preserve Google MediaPipe Tasks Vision native classes & JNI
-keep class com.google.mediapipe.** { *; }
-keep interface com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Room SQLite Entities & Type Converters
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Data & Domain Models (serialization safety)
-keep class com.example.data.local.room.** { *; }
-keep class com.example.domain.model.** { *; }

# Google Credential Manager & Firebase
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
