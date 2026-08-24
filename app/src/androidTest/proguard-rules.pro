# Error Prone's IncompatibleModifiers annotation references the JDK compiler model only in
# annotation metadata. AndroidJUnitRunner never loads javax.lang.model.element.Modifier at runtime.
# Keep R8 fail-closed for every other missing type and suppress only this compile-time-only symbol.
-dontwarn javax.lang.model.element.Modifier

# Release AndroidTest is minified. AndroidJUnitRunner, androidx.test.platform and Phase107 test
# support require Kotlin, coroutines and tracing runtime classes even when R8 cannot see every
# reflective/cross-APK call edge. Preserve those runtime families in the instrumentation APK while
# keeping release test shrinking enabled.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.tracing.** { *; }
