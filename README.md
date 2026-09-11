# AProxyI — NetworkInspector

An on-device network and analytics inspector for Android. Drop it into any app,
inspect every request, response and analytics event from the phone itself — no
laptop, no proxy, no CA certificate, no root.

Unlike Chucker, it is **not tied to OkHttp**. The core is a callback API you can
drive from any HTTP client (Retrofit, Volley, Ktor, a hand-rolled
`HttpURLConnection` layer), with an OkHttp interceptor supplied as an optional
convenience. It also captures **analytics events** (Firebase, CleverTap,
AppsFlyer, Facebook), which network inspectors generally do not.

## Debug-only by construction

The inspector captures `Authorization` headers, cookies, full request and
response bodies, and analytics payloads. In a consumer app that means addresses,
phone numbers and order data. Shipping that capture code in a release build is a
data-leak surface, so this project makes it **structurally impossible** rather
than relying on a runtime flag.

The library ships as two interchangeable artifacts with an identical public API:

| Module           | Wired via                | Contains                                      |
|------------------|--------------------------|-----------------------------------------------|
| `library`        | `debugImplementation`    | The real implementation: capture, storage, UI  |
| `library-no-op`  | `releaseImplementation`  | Hollow stubs. No capture, no UI, no permission |
| `library-api`    | transitive (both)        | Inert data models shared by the two above      |

A release build therefore contains **no capture code at all** — nothing to strip,
nothing to accidentally re-enable, and no `POST_NOTIFICATIONS` permission or
activity merged into your manifest.

This is deliberately stronger than `if (BuildConfig.DEBUG)`, which would refer to
the *library's* `BuildConfig` rather than the host app's, and would leave the
capture code sitting in the release APK relying on R8 to remove it.

## Integration

```kotlin
dependencies {
    debugImplementation("com.networkinspector:library:1.0.0")
    releaseImplementation("com.networkinspector:library-no-op:1.0.0")
}
```

Initialise once, in `Application.onCreate()`:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkInspector.init(this)
    }
}
```

The same call compiles in both variants. In release it resolves to an empty
method.

### With OkHttp

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(NetworkInspectorInterceptor())
    .build()
```

In release this resolves to the no-op interceptor, which simply calls
`chain.proceed(chain.request())`.

### With any other HTTP client

```kotlin
val id = NetworkInspector.onRequestStart(url, "POST", body = payload)
// ...
NetworkInspector.onRequestSuccess(id, 200, responseBody)
// or
NetworkInspector.onRequestFailed(id, 500, exception)
```

Or via the fluent wrapper:

```kotlin
NetworkInspectorWrapper.request()
    .url(url)
    .post()
    .body(payload)
    .execute { api.createOrder(payload) }
```

### Analytics events

```kotlin
AnalyticsInspector.logEvent("add_to_cart", bundle, AnalyticsSource.FIREBASE)
```

### Opening the UI

There are two ways in. **Wire up the manual entry point** — it is the reliable
one; treat the notification as a convenience.

```kotlin
NetworkInspector.launch(context)          // network requests
NetworkInspector.launchAnalytics(context) // analytics events
```

Hang that off a debug drawer item, an overflow menu entry, or a shake detector.
It works immediately after `init()`, before any request has been made.

## Notifications

The library shows an ongoing notification with live request counts. Tapping it
opens the inspector; the **Clear** action empties the captured list.

### What you do NOT need to write

Nothing wires the notification tap. `RequestListActivity` is declared in the
library's own manifest, manifest merger pulls it into your app, and the library
builds the `PendingIntent` itself. Tapping the notification opens the inspector
with no app-side code.

### What you DO need to write

**1. Initialise the library.** Without this no notification manager is
constructed, so no notification ever appears:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkInspector.init(this)
    }
}
```

**2. Request `POST_NOTIFICATIONS` at runtime on Android 13+ (API 33).**

This is the one that catches people. The library *declares* the permission in its
manifest, but on API 33+ declaring is not granting. Without the runtime grant,
`NotificationManagerCompat.notify()` throws `SecurityException`, which the
library catches and swallows — so you get no notification, no entry point, and
no error telling you why.

```kotlin
// Debug builds only.
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQ_NOTIFICATIONS
        )
    }
}
```

> **If the notification never shows up, check this first.** The swallowed
> `SecurityException` is by far the most common cause.

### Two known limitations

- **The notification only appears after the first request.** It is refreshed from
  the request lifecycle and is not posted at `init()`, so until traffic flows
  there is no notification to tap.
- **Clear dismisses it.** The Clear action empties the list and removes the
  notification, so the entry point disappears until the next request.

Both are why you want the manual `NetworkInspector.launch(context)` entry point
above rather than relying on the notification alone.

## The parity contract

`:library` and `:library-no-op` must expose exactly the same public signatures.
Adding a public method to one without the other breaks the consumer's **release**
build at compile time — loud and early, which is the intended failure mode. Keep
shared data models in `:library-api` so they cannot drift.

The tradeoff: `:library-api` ships in release builds. It is inert — data classes
with no capture, storage, UI or permissions — and nothing ever populates them, so
it costs a little method count in exchange for eliminating model drift entirely.

## Status

Early. Known gaps:

- **Resource files are missing.** The UI is Views-based and will not compile
  until `library/src/main/res/` is populated. Needed: the view-binding layouts
  `activity_request_list` and `activity_request_detail`; the layouts
  `activity_analytics_list`, `activity_analytics_detail`, `item_request`,
  `item_analytics_event`; the menus `menu_request_list`, `menu_request_detail`;
  the colors `ni_accent`, `ni_success`, `ni_warning`, `ni_info`; and the drawable
  `ic_network_inspector`.
- **Known defects.** See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) — 17 open issues,
  including three credential-leak paths and two crash risks.
- No Gradle wrapper yet — run `gradle wrapper` to add one.
- Not yet published to a Maven repository; consume via `includeBuild` or a local
  `mavenLocal()` publish for now.
- Nothing here has been compiled or run yet.
