# Public surface mirrored by :library-no-op, and pinned by Binary Compatibility
# Validator. Keep it stable so swapping artifacts between debug and release
# cannot change what consumers link against.
-keep class com.aproxyi.AProxyI { public *; }
-keep class com.aproxyi.AProxyIWrapper { public *; }
-keep class com.aproxyi.AProxyIWrapper$* { public *; }
-keep class com.aproxyi.AnalyticsInspector { public *; }
-keep class com.aproxyi.core.** { public *; }
-keep class com.aproxyi.interceptor.** { public *; }

# Implementation detail: everything under .internal is Kotlin-internal and is
# free to be renamed or removed.
