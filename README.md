# NetworkInspector

[![CI](https://github.com/mnshlohia/AProxyI/actions/workflows/ci.yml/badge.svg)](https://github.com/mnshlohia/AProxyI/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

An on-device network and analytics inspector for Android. Drop it into any app
and inspect every request, response and analytics event **from the phone
itself** — no laptop, no proxy, no CA certificate, no root.

Because it runs inside the app, above the TLS layer, certificate pinning does
not block it.

## Why not just use Chucker

Two differences, both deliberate:

|                        | NetworkInspector                                    | Chucker                       |
|------------------------|-----------------------------------------------------|-------------------------------|
| HTTP client            | **Any** — callback API; OkHttp interceptor optional  | OkHttp only                   |
| Analytics events       | **Yes** — Firebase, CleverTap, AppsFlyer, Facebook   | No                            |
| Redaction by default   | **8 headers + query params**, at capture             | None (`headersToRedact` empty)|
| URL query redaction    | **Yes**                                              | No                            |
| Storage                | **In memory only**                                   | Room, persisted to disk       |

Staying in memory is a choice, not a missing feature: captured traffic contains
live credentials, and disk persistence outlives the debugging session.

---

## Installation

```kotlin
// settings.gradle.kts — until published to Maven Central
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()   // if you ran ./gradlew publishToMavenLocal
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    debugImplementation("io.github.mnshlohia.networkinspector:library:0.1.0")
    releaseImplementation("io.github.mnshlohia.networkinspector:library-no-op:0.1.0")
}
```

Both artifacts expose an identical public API, so the same code compiles in both
variants. **Both lines are required** — omit the `releaseImplementation` and your
release build will not compile.

Minimum SDK 21. Compiled against SDK 35, Kotlin 2.0, AGP 8.7.

---

## Setup

### 1. Initialise

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkInspector.init(this)
    }
}
```

In release this resolves to an empty method. `init()` is idempotent — a second
call logs a warning and returns.

With custom configuration:

```kotlin
NetworkInspector.init(
    this,
    NetworkInspectorConfig(
        maxRequests = 500,
        maxBodySize = 200_000,
        showNotification = true,
        logToLogcat = true,
        excludedHosts = listOf("""analytics\."""),   // matched against host only
        excludedPaths = listOf("^/health$"),         // matched against path only
        activeRequestTimeoutMs = 60_000L
    )
)
```

### 2. Add an entry point

```kotlin
NetworkInspector.launch(context)           // network requests
NetworkInspector.launchAnalytics(context)  // analytics events
```

Hang this off a debug drawer item, an overflow entry, or a shake detector. It
works immediately after `init()`, before any request has been made.

There is also an ongoing notification — see [Notifications](#notifications).

### 3. Capture traffic

Pick whichever fits your networking layer.

#### OkHttp / Retrofit

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(NetworkInspectorInterceptor())
    .build()
```

In release this resolves to a no-op interceptor that calls
`chain.proceed(chain.request())`.

> Register it as an **application** interceptor to see the call as your code
> issued it, or as a **network** interceptor to see it as it went on the wire
> (post-redirect, post-gzip, with real connection headers).

#### Any other client — the callback API

```kotlin
val id = NetworkInspector.onRequestStart(
    url = url,
    method = "POST",
    headers = headers,
    body = payload
)

// exactly one of:
NetworkInspector.onRequestSuccess(id, 200, responseBody, responseHeaders)
NetworkInspector.onRequestFailed(id, 500, exception)
NetworkInspector.onRequestCancelled(id)
```

**You must call one of the three.** A request with no completion call is swept
into `TIMED_OUT` after `activeRequestTimeoutMs` — see
[Stale in-flight requests](#stale-in-flight-requests).

#### Fluent wrapper

```kotlin
// Automatic success/failure tracking around a block
val result = NetworkInspectorWrapper.track(url) { api.fetch() }

// When you have a status code
NetworkInspectorWrapper.trackWithCode(url) { api.fetchWithCode() }

// Coroutines
NetworkInspectorWrapper.trackSuspend(url) { api.fetchSuspending() }

// Builder
NetworkInspectorWrapper.request()
    .url(url)
    .post()
    .header("X-Request-Id", id)
    .body(payload)
    .execute { api.createOrder(payload) }
```

#### Wrapping existing callbacks

```kotlin
val tracker = CallbackInterceptor.create<OrderResponse>(url, "POST", body = payload)

api.createOrder(payload, object : Callback<OrderResponse> {
    override fun onSuccess(response: OrderResponse) {
        tracker.onSuccess(200, response)
        // your handling
    }
    override fun onError(e: Throwable) {
        tracker.onFailure(500, e)
        // your handling
    }
})
```

### 4. Capture analytics events

```kotlin
// Alongside your real analytics call
firebaseAnalytics.logEvent(name, bundle)
AnalyticsInspector.logEvent(name, bundle, AnalyticsSource.FIREBASE)
```

`AnalyticsSource` covers `FIREBASE`, `CLEVERTAP`, `APPSFLYER` and `FACEBOOK`.
Both a `Bundle` and a `Map<String, Any?>` overload exist.

`NetworkInspector.init()` drives `AnalyticsInspector` too — you do not enable it
separately.

---

## Debug-only by construction

The inspector captures `Authorization` headers, cookies, full request and
response bodies, and analytics payloads. In a consumer app that means addresses,
phone numbers and order data. Shipping that capture code in a release build is a
data-leak surface, so this project makes it **structurally impossible** rather
than relying on a runtime flag.

| Module          | Wired via                | Contains                                       |
|-----------------|--------------------------|------------------------------------------------|
| `library`       | `debugImplementation`    | Real implementation: capture, storage, UI      |
| `library-no-op` | `releaseImplementation`  | Hollow stubs. No capture, no UI, no permission |
| `library-api`   | transitive (both)        | Inert data models shared by the two            |

This is stronger than `if (BuildConfig.DEBUG)`, which inside a library resolves
against the *library's* `BuildConfig` rather than the host app's, and would leave
the capture code sitting in the release APK relying on R8 to remove it.

**Verified on every CI run**, not asserted. From the sample app's APKs:

| | Debug | Release |
|---|---|---|
| `com.networkinspector.internal` classes | 54 | **0** |
| Inspector activities in merged manifest | 4 | **0** |
| `POST_NOTIFICATIONS` permission | yes | **no** |
| APK size | 6.5 MB | 2.9 MB |

What remains in release is the no-op stubs plus the inert models — data classes
that nothing ever populates.

---

## Redaction

Sensitive values are redacted **at capture, irreversibly**. They never enter the
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
NetworkInspectorConfig(
    redactedHeaders = NetworkInspectorConfig.DEFAULT_REDACTED_HEADERS + "X-Internal-Sig",
    redactedQueryParams = NetworkInspectorConfig.DEFAULT_REDACTED_QUERY_PARAMS + "uid"
)
```

**The tradeoff:** because redaction is irreversible, you cannot read back a real
token to reproduce a failing call in curl. That was deliberate — a debug tool
holding live credentials in memory is one careless screenshot away from leaking
them. If you need the raw value, log it yourself at the call site.

---

## Notifications

An ongoing notification shows live request counts. Tapping it opens the
inspector; the **Clear** action empties the captured list.

**You write no code for this.** `RequestListActivity` is declared in the
library's own manifest, manifest merger pulls it into your app, and the library
builds the `PendingIntent` itself.

**Including the permission.** On Android 13+ `POST_NOTIFICATIONS` must be granted
at runtime, not merely declared — the library requests it itself the first time
you open the inspector UI. Deny it and everything still works; you lose only the
notification shortcut. The library catches the resulting `SecurityException`, so
a denial can never crash your app.

Two limitations worth knowing:

- **The notification only appears after the first request.** It is refreshed from
  the request lifecycle and is not posted at `init()`.
- **Clear dismisses it**, until the next request.

Both are why you want the manual `NetworkInspector.launch(context)` entry point
as the primary route.

---

## Stale in-flight requests

The callback API depends on you calling `onRequestSuccess` / `onRequestFailed` /
`onRequestCancelled`. When a path forgets, the request would otherwise sit in the
in-flight map forever and permanently inflate the "N active" count.

Requests with no completion call within `activeRequestTimeoutMs` (default 60s)
are swept into `RequestStatus.TIMED_OUT` and counted as failed, so a missed
callback surfaces as a visible entry rather than silently skewing the stats. Set
the timeout to `0` to disable the sweep.

---

## API surface and the parity contract

Everything under `com.networkinspector.internal.*` is Kotlin-`internal` — the UI,
the notification manager, the body formatter. It is an implementation detail and
free to change. The public surface is deliberately small: about 24 entry points
across `NetworkInspector`, `AnalyticsInspector`, `NetworkInspectorWrapper`, the
two interceptors, and the models in `com.networkinspector.core`.

That matters because `library` and `library-no-op` must expose an **identical**
public API. Consumers link one in debug and the other in release, so any drift
breaks the consumer's *release* build. Every public member is one the no-op must
mirror by hand.

[Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
pins it:

```bash
./gradlew apiDump    # regenerate the checked-in *.api files after an API change
./gradlew apiCheck   # fails if code and *.api have diverged
```

CI additionally diffs `library/api/library.api` against
`library-no-op/api/library-no-op.api` and fails if they differ. **After changing
any public signature, run `apiDump` and commit the result.**

---

## Building

```bash
./gradlew assembleDebug assembleRelease   # all modules
./gradlew apiCheck                        # public API unchanged
./gradlew :sample:assembleRelease         # proves both artifacts compile
./gradlew publishToMavenLocal             # install locally for testing
```

Requires JDK 17+ and the Android SDK (compileSdk 35, build-tools 35.0.0). Point
`local.properties` at it:

```properties
sdk.dir=/path/to/android-sdk
```

### Project layout

```
library/           real implementation      → debugImplementation
  └ internal/      UI, notification, util   (Kotlin-internal)
library-no-op/     hollow stubs             → releaseImplementation
library-api/       shared inert models      (ships in both)
sample/            consumer app; exercises every public entry point
```

The sample is not decorative: it links `:library` in debug and `:library-no-op`
in release and calls every public method, so `:sample:assembleRelease` fails if
the two artifacts ever drift.

---

## Contributing

1. `./gradlew assembleDebug assembleRelease :sample:assembleRelease`
2. If you changed a public signature, mirror it in `library-no-op` **and** run
   `./gradlew apiDump`, committing the updated `*.api` files.
3. Keep new implementation code under `com.networkinspector.internal.*` and
   `internal`, so it stays out of the surface the no-op has to mirror.

## License

Apache 2.0 — see [LICENSE](LICENSE).
