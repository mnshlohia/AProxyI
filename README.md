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

```kotlin
NetworkInspector.launch(context)
NetworkInspector.launchAnalytics(context)
```

Also reachable by tapping the ongoing notification.

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

- **Resource files are missing.** The UI is Views-based and references layouts,
  menus and colors (`activity_analytics_list`, `item_request`,
  `menu_request_detail`, `ni_accent`, …) that are not yet in the repo. The
  project will not compile until `library/src/main/res/` is populated.
- No Gradle wrapper yet — run `gradle wrapper` to add one.
- Not yet published to a Maven repository; consume via `includeBuild` or a local
  `mavenLocal()` publish for now.
- Nothing here has been compiled or run yet.
