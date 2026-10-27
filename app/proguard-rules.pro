# JNA y los bindings de UniFFI se resuelven por reflexión y por nombre de símbolo.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class io.github.ricardomonroy.tlacuache.ffi.** { *; }
-dontwarn java.awt.**
