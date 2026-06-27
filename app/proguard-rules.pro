# Nadi ProGuard Rules

# ─── Kotlin ───────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers class kotlin.Metadata { *; }

# ─── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}

# ─── NanoHTTPD ────────────────────────────────────────────────────────────────
-keep class fi.iki.elonen.** { *; }
-keep class org.nanohttpd.** { *; }

# ─── OkHttp & Okio ────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ─── ZXing ────────────────────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }

# ─── Coil ─────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ─── Aplikasi Nadi ────────────────────────────────────────────────────────────
# Preserve data/model classes (Room entities & domain models)
-keep class com.danis.nadi.model.** { *; }
-keep class com.danis.nadi.data.db.entity.** { *; }
-keep class com.danis.nadi.history.** { *; }

# Keep exception info for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile