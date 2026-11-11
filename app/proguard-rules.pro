# Compose and Kotlin metadata are handled by the bundled AGP rules.
# Keep the GL renderer's shader source strings intact (they are plain constants,
# but keep the class so reflection-free lookups in logs stay readable).
-keep class com.ember.xmb.gl.** { *; }

# The launcher never obfuscates its own entry point.
-keep class com.ember.xmb.MainActivity { *; }
