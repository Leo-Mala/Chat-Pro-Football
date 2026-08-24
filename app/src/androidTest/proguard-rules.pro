# Error Prone's IncompatibleModifiers annotation references the JDK compiler model only in
# annotation metadata. AndroidJUnitRunner never loads javax.lang.model.element.Modifier at runtime.
# Keep R8 fail-closed for every other missing type and suppress only this compile-time-only symbol.
-dontwarn javax.lang.model.element.Modifier
