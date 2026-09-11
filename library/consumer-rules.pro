# Public surface mirrored by :library-no-op, and pinned by Binary Compatibility
# Validator. Keep it stable so swapping artifacts between debug and release
# cannot change what consumers link against.
-keep class com.networkinspector.NetworkInspector { public *; }
-keep class com.networkinspector.NetworkInspectorWrapper { public *; }
-keep class com.networkinspector.NetworkInspectorWrapper$* { public *; }
-keep class com.networkinspector.AnalyticsInspector { public *; }
-keep class com.networkinspector.core.** { public *; }
-keep class com.networkinspector.interceptor.** { public *; }

# Implementation detail: everything under .internal is Kotlin-internal and is
# free to be renamed or removed.
