# Public surface mirrored by :library-no-op. Keep it stable so that swapping
# artifacts between debug and release cannot change what consumers link against.
-keep class com.networkinspector.NetworkInspector { *; }
-keep class com.networkinspector.NetworkInspector$RequestListener { *; }
-keep class com.networkinspector.NetworkInspectorWrapper { *; }
-keep class com.networkinspector.AnalyticsInspector { *; }
-keep class com.networkinspector.AnalyticsInspector$EventListener { *; }
-keep class com.networkinspector.core.** { *; }
-keep class com.networkinspector.interceptor.** { *; }
