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

# Release instrumentation is loaded into the target application process. AndroidJUnitRunner,
# androidx.test.platform and the release instrumented support code resolve runtime entry points
# through that process classloader before/during test execution. Keep the required runtime families
# available under their original names while retaining R8 for all other production code.
-keep class androidx.tracing.Trace { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# The Release target and Release AndroidTest APKs are minified independently but share Lifecycle,
# Compose UI and Compose runtime types across the instrumentation boundary. Keep those ABIs stable
# in the target APK so separately optimized test bytecode cannot reference removed/renamed symbols.
-keep class androidx.lifecycle.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.runtime.** { *; }

# Compose UI test synchronisation delegates to Espresso, whose runtime references the standalone
# ListenableFuture ABI from the target process. Preserve only that required Guava-compatible type
# instead of retaining the whole com.google.common tree.
-keep class com.google.common.util.concurrent.ListenableFuture { *; }
