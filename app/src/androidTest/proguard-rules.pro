# Error Prone's IncompatibleModifiers annotation references the JDK compiler model only in
# annotation metadata. AndroidJUnitRunner never loads javax.lang.model.element.Modifier at runtime.
# Keep R8 fail-closed for every other missing type and suppress only this compile-time-only symbol.
-dontwarn javax.lang.model.element.Modifier

# Release AndroidTest is minified. AndroidJUnitRunner and androidx.test.platform execute from the
# instrumentation APK and require Kotlin runtime and androidx.tracing classes even when individual
# test methods do not reference every one directly. Keep those runtime dependencies in the test APK
# only, preserving production Release shrinking while preventing runner startup crashes.
-keep class kotlin.** { *; }
-keep class androidx.tracing.** { *; }
