# Keep Application class and Activities
-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Activity { *; }

# Keep Data Models, Room Entities, DAOs and JSON DTOs
-keep class com.example.data.** { *; }
-keepclassmembers class com.example.data.** { *; }

# Keep ViewModels and UI States
-keep class com.example.ui.viewmodel.** { *; }
-keep class com.example.usecase.** { *; }

# Moshi rules
-keep class com.squareup.moshi.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.squareup.moshi.**

# Room Database rules
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabaseKt
-dontwarn androidx.room.paging.**

# Hilt / Dagger rules
-keep class com.example.MainApplication { *; }
-keep class com.example.** { *; }
-keep class dagger.hilt.** { *; }
-keep class hilt_aggregated_deps.** { *; }

# Installed Release instrumentation executes AndroidJUnitRunner inside the target application process.
# androidx.test.runner references androidx.tracing.Trace at runtime, while the production app has no
# direct call site. Preserve this transitive runtime entry point so target R8 cannot remove it before
# the minified Release AndroidTest APK is installed. Referenced tracing implementation helpers remain
# reachable from this kept class and are retained by R8 normally.
-keep class androidx.tracing.Trace { *; }
