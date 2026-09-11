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

**2. Nothing, for notification permissions.**

On Android 13+ `POST_NOTIFICATIONS` must be granted at runtime, not merely
declared. The library requests it itself the first time you open the inspector
UI, so you do not write the permission dance in your app.

Deny it and everything still works — you lose only the notification shortcut,
and reach the inspector through the manual entry point below. (The library
catches the resulting `SecurityException` when posting, so a denial can never
crash your app.)


### Two known limitations

- **The notification only appears after the first request.** It is refreshed from
  the request lifecycle and is not posted at `init()`, so until traffic flows
  there is no notification to tap.
- **Clear dismisses it.** The Clear action empties the list and removes the
  notification, so the entry point disappears until the next request.

Both are why you want the manual `NetworkInspector.launch(context)` entry point
above rather than relying on the notification alone.

## Redaction

Sensitive values are redacted **at capture, irreversibly** — they never enter the
in-memory store, so they cannot escape through the inspector UI, the share
action, or copy-as-cURL. This applies to the callback API and the OkHttp
interceptor alike.

Redacted by default:

- **Headers** — `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`,
  `X-Api-Key`, `X-Auth-Token`, `X-Access-Token`, `X-Csrf-Token`
- **URL query parameters and `params`** — names matching `key`, `sid`, `sig`,
  `otp`, `pin` exactly, or *containing* `token`, `secret`, `password`, `passwd`,
  `pwd`, `auth`, `signature`, `session`, `apikey`, `api_key`, `credential`. That
  covers `access_token`, `oauth_token` and `client_secret` without enumerating
  every vendor spelling.

```kotlin
NetworkInspector.init(
    this,
    NetworkInspectorConfig.DEBUG.copy(
        redactedHeaders = NetworkInspectorConfig.DEFAULT_REDACTED_HEADERS + "X-Internal-Sig",
        redactedQueryParams = NetworkInspectorConfig.DEFAULT_REDACTED_QUERY_PARAMS + "uid"
    )
)
```

**The tradeoff:** because redaction is irreversible, you cannot read back a real
token to reproduce a failing call in curl. That was a deliberate choice — a
debug tool holding live credentials in memory is one careless screenshot away
from leaking them. If you need the raw value, log it yourself at the call site.

## Stale in-flight requests

The callback API relies on you calling `onRequestSuccess` / `onRequestFailed`.
When a path forgets to, the request would otherwise sit in the in-flight map
forever and permanently inflate the "N active" count in the notification.

Requests with no completion call within `activeRequestTimeoutMs` (default 60s)
are therefore swept into `RequestStatus.TIMED_OUT` and counted as failed, so a
missed callback shows up as a visible entry instead of silently skewing the
stats. Set the timeout to `0` to disable the sweep.

## API surface and the parity contract

Everything under `com.networkinspector.internal.*` is Kotlin-`internal`: the UI,
the notification manager, and the body formatter. It is an implementation detail
and free to change. The public surface is deliberately small — roughly 24 entry
points across `NetworkInspector`, `AnalyticsInspector`,
`NetworkInspectorWrapper`, the two interceptors, and the models in
`com.networkinspector.core`.

That matters because `:library` and `:library-no-op` must expose an *identical*
public API — consumers link one in debug and the other in release, so any drift
breaks the consumer's **release** build. Every public member is a member the
no-op has to mirror by hand, so a small surface is a small maintenance burden.

[Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
pins it:

```
./gradlew apiDump    # regenerate the checked-in *.api files after an API change
./gradlew apiCheck   # fails if code and *.api have diverged (wired into `check`)
```

Diffing `library/api/library.api` against `library-no-op/api/library-no-op.api`
is the parity check. Run `apiDump` and commit the result whenever you change a
public signature on either side.

Shared data models live in `:library-api` so the two modules cannot drift on
types. The tradeoff: that module ships in release builds. It is inert — data
classes with no capture, storage, UI or permissions, and nothing populates them
at runtime — so it costs a little method count in exchange for removing a whole
class of bug.

## Status

Early. Known gaps:

- **Resource files are missing.** The UI is Views-based and will not compile
  until `library/src/main/res/` is populated. Needed: the view-binding layouts
  `activity_request_list` and `activity_request_detail`; the layouts
  `activity_analytics_list`, `activity_analytics_detail`, `item_request`,
  `item_analytics_event`; the menus `menu_request_list`, `menu_request_detail`;
  the colors `ni_accent`, `ni_success`, `ni_warning`, `ni_info`; and the drawable
  `ic_network_inspector`.
- **Known defects.** See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) — 3 open, 14 fixed.
- **Nothing has been compiled.** The fixes were written without an Android SDK
  available, so they are unverified.
- No Gradle wrapper yet — run `gradle wrapper` to add one.
- Not yet published to a Maven repository; consume via `includeBuild` or a local
  `mavenLocal()` publish for now.
- Nothing here has been compiled or run yet.
