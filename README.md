<div align="center">

# AProxyI

**On-device network and analytics inspector for Android.**

No laptop. No proxy. No CA certificate. No root.

[![CI](https://github.com/mnshlohia/AProxyI/actions/workflows/ci.yml/badge.svg)](https://github.com/mnshlohia/AProxyI/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://developer.android.com/about/versions/lollipop)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)

</div>

---

Inspect every request, response and analytics event from the phone itself.
Because AProxyI runs inside your app, above the TLS layer, certificate pinning
does not block it.

## Features

- **Works with any HTTP client.** The core is a callback API. Retrofit, OkHttp,
  Ktor, Volley, `HttpURLConnection` — an OkHttp interceptor is provided, not required.
- **Captures analytics events.** Firebase, CleverTap, AppsFlyer and Facebook
  events in a dedicated screen, alongside network traffic.
- **Secrets redacted by default.** Eight credential headers plus token-shaped
  query parameters, scrubbed *at capture* so they never enter memory.
- **Absent from release builds.** Not disabled — absent. A no-op artifact
  replaces the real one, and CI pulls apart the APK to prove it every push.
- **Full request detail.** Headers, bodies, timings, sizes, pretty-printed JSON,
  copy-as-cURL, search and status filtering.
- **Catches your mistakes.** A request you forget to complete surfaces as
  `TIMED_OUT` instead of silently skewing the counts.

## How it compares

|                      | AProxyI                                             | Chucker                        |
|----------------------|-----------------------------------------------------|--------------------------------|
| HTTP client          | **Any** — callback API; OkHttp interceptor optional  | OkHttp only                    |
| Analytics events     | **Yes** — Firebase, CleverTap, AppsFlyer, Facebook   | No                             |
| Redaction by default | **8 headers + query params**, at capture             | None (`headersToRedact` empty) |
| URL query redaction  | **Yes**                                              | No                             |
| Storage              | **In memory only**                                   | Room, persisted to disk        |

Staying in memory is a choice, not a gap: captured traffic holds live
credentials, and disk persistence outlives the debugging session.

## Contents

**Getting started**
&nbsp;&nbsp;[Quick start](#quick-start)
· [The one thing to understand](#the-one-thing-to-understand)
· [Installation](#installation)

**Using it**
&nbsp;&nbsp;[Integration recipes](#integration-recipes)
· [Analytics events](#analytics-events)
· [Configuration](#configuration)
· [Best practices](#best-practices)
· [Troubleshooting](#troubleshooting)

**Understanding it**
&nbsp;&nbsp;[How it works](#how-it-works)
· [API surface and the parity contract](#api-surface-and-the-parity-contract)

**Working on it**
&nbsp;&nbsp;[Building](#building)
· [Contributing](#contributing)
· [Status](#status)
· [License](#license)

## Quick start

**1. Add both dependencies.** Both lines are required.

```kotlin
// app/build.gradle.kts
dependencies {
    debugImplementation("io.github.mnshlohia.aproxyi:library:0.1.0")
    releaseImplementation("io.github.mnshlohia.aproxyi:library-no-op:0.1.0")
}
```

**2. Initialise in `Application.onCreate()`.**

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AProxyI.init(this)
    }
}
```

**3. Capture traffic.** With OkHttp or Retrofit, that's one interceptor:

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(authInterceptor)
    .addInterceptor(AProxyIInterceptor())   // add LAST — see below
    .build()
```

**4. Open it.** Wire this to a debug menu item, long-press, or shake gesture:

```kotlin
AProxyI.launch(context)
```

That's the whole integration. There is also an ongoing notification you can tap.

---

## The one thing to understand

**Write your integration code in your normal `main` source set. Do not guard it
with `if (BuildConfig.DEBUG)`, and do not put it in a `debug/` source set.**

The two artifacts expose an *identical* public API. In debug you link the real
implementation; in release you link hollow stubs where every method is empty. So
this line:

```kotlin
AProxyI.init(this)
```

compiles in both variants and does nothing in release. The release APK contains
no capture code at all — not disabled, **absent**.

```kotlin
// Correct — plain code in the main source set
AProxyI.init(this)
AProxyI.launch(context)

// Unnecessary — the no-op already handles this
if (BuildConfig.DEBUG) { AProxyI.init(this) }
```

The only thing you may want to guard is your *entry point UI* — a "Developer
tools" menu item — since in release it would open nothing.

```kotlin
// AProxyI.isEnabled() is false in release builds
if (AProxyI.isEnabled()) {
    menu.add("AProxyI").setOnMenuItemClickListener {
        AProxyI.launch(this); true
    }
}
```

---

## Installation

Until this is on Maven Central, publish locally:

```bash
git clone https://github.com/mnshlohia/AProxyI
cd AProxyI
./gradlew publishToMavenLocal
```

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    debugImplementation("io.github.mnshlohia.aproxyi:library:0.1.0")
    releaseImplementation("io.github.mnshlohia.aproxyi:library-no-op:0.1.0")
}
```

Requires minSdk 21. Built against compileSdk 35, Kotlin 2.0, AGP 8.7.

> **If you omit the `releaseImplementation` line, your release build will not
> compile.** That is deliberate: it is a loud, early failure instead of a silent
> one.

---

## Integration recipes

Pick the one matching your networking layer. You only need one.

### Retrofit / OkHttp

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(authInterceptor)
    .addInterceptor(AProxyIInterceptor())
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .client(client)
    .build()
```

**Interceptor ordering matters.** OkHttp runs application interceptors in the
order you add them, so:

- Add the inspector **last** to see the request as it finally goes out, with
  headers your auth/header interceptors added.
- Add it **first** to see the request exactly as your code built it, before
  anything else touched it.
- Use `addNetworkInterceptor` instead to see the true wire form — after
  redirects, with gzip and real connection headers.

Most people want it **last**.

### Coroutines / suspend functions

```kotlin
suspend fun fetchOrders(): List<Order> =
    AProxyIWrapper.trackSuspend("$BASE_URL/orders") {
        api.getOrders()
    }
```

Failures are recorded and rethrown, so your error handling is unchanged.

### Ktor, Volley, HttpURLConnection — any client

Use the callback API. Call `onRequestStart`, keep the returned id, then call
**exactly one** completion method.

```kotlin
val id = AProxyI.onRequestStart(
    url = url,
    method = "POST",
    headers = headers,
    body = jsonPayload,
    tag = "checkout"          // optional; searchable in the UI
)

try {
    val response = client.execute(request)
    AProxyI.onRequestSuccess(id, response.code, response.body, response.headers)
} catch (e: IOException) {
    AProxyI.onRequestFailed(id, 0, e)
}
```

> **Always complete the request.** A request with no completion call is swept
> into `TIMED_OUT` after 60s and shows up as failed. That is by design — it makes
> a forgotten callback visible instead of silently inflating the active count.

### Blocking calls — let the wrapper do it

```kotlin
val result = AProxyIWrapper.track("$BASE_URL/profile") {
    api.getProfile()          // success and failure recorded automatically
}

// When you have a status code to report
val body = AProxyIWrapper.trackWithCode("$BASE_URL/profile") {
    val r = api.getProfile()
    r.code to r.body
}
```

### Builder style

```kotlin
AProxyIWrapper.request()
    .url("$BASE_URL/orders")
    .post()
    .header("X-Request-Id", requestId)
    .body(payload)
    .tag("checkout")
    .execute { api.createOrder(payload) }
```

### Existing callback-based clients

```kotlin
val tracker = CallbackInterceptor.create<OrderResponse>(
    url = "$BASE_URL/orders",
    method = "POST",
    body = payload
)

api.createOrder(payload, object : Callback<OrderResponse> {
    override fun onSuccess(response: OrderResponse) {
        tracker.onSuccess(200, response)
        // your existing handling
    }

    override fun onError(code: Int, e: Throwable) {
        tracker.onFailure(code, e)
        // your existing handling
    }
})
```

---

## Analytics events

Log to the inspector alongside your real analytics call. The cleanest way is a
thin wrapper you call instead of the SDK directly:

```kotlin
object Analytics {
    fun log(name: String, params: Bundle) {
        Firebase.analytics.logEvent(name, params)
        AnalyticsInspector.logEvent(name, params, AnalyticsSource.FIREBASE)
    }
}
```

Sources: `FIREBASE`, `CLEVERTAP`, `APPSFLYER`, `FACEBOOK`. There is a `Bundle`
overload and a `Map<String, Any?>` overload.

```kotlin
AnalyticsInspector.logEvent("add_to_cart", mapOf("sku" to sku, "qty" to 2))
```

View them with `AProxyI.launchAnalytics(context)`.

`AProxyI.init()` drives `AnalyticsInspector` too — you do not enable it
separately.

---

## Configuration

```kotlin
AProxyI.init(
    this,
    AProxyIConfig(
        maxRequests = 500,
        maxBodySize = 200_000,
        excludedHosts = listOf("""firebase\.""", """crashlytics\."""),
        excludedPaths = listOf("^/health$", "^/ping$"),
        redactedHeaders = AProxyIConfig.DEFAULT_REDACTED_HEADERS + "X-Internal-Sig",
        activeRequestTimeoutMs = 60_000L
    )
)
```

| Option | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch. `false` captures nothing. |
| `showNotification` | `true` | Ongoing notification with live counts. |
| `maxRequests` | `500` | Ring size; oldest are dropped. |
| `maxBodySize` | `200_000` | Max stored body length, in characters. |
| `logToLogcat` | `true` | Mirror captures to Logcat. |
| `notificationChannelName` | `"AProxyI"` | Channel name shown in settings. |
| `excludedHosts` | empty | Regexes matched against the **host only**. |
| `excludedPaths` | empty | Regexes matched against the **path only**. |
| `redactedHeaders` | 8 headers | Header names whose values are replaced. |
| `redactedQueryParams` | see below | Query names whose values are replaced. |
| `activeRequestTimeoutMs` | `60_000` | Sweep in-flight requests after this. `0` disables. |

Presets: `AProxyIConfig.DEBUG` (everything on) and
`AProxyIConfig.RELEASE` (everything off).

`excludedHosts` and `excludedPaths` really do scope to host and path — an
`excludedHosts` pattern will not match the same text appearing in a path.

---

## Best practices

**Do**

- Put integration code in `main`, unguarded. That is what the no-op is for.
- Add the OkHttp interceptor **last** so you see final headers.
- Give requests a `tag` — it is searchable in the UI.
- Exclude your own analytics and crash-reporting hosts, so the list stays
  readable.
- Provide a manual entry point (`launch()`); do not rely only on the
  notification.

**Don't**

- Don't wrap calls in `if (BuildConfig.DEBUG)` — redundant, and it inverts the
  point of the no-op artifact.
- Don't call `init()` more than once. It is idempotent; a second call logs a
  warning and is ignored.
- Don't forget a completion call on the callback API.
- Don't expect a real token back — redaction is irreversible by design.
- Don't read `getRequests()` expecting a request you just completed: capture is
  asynchronous (see below).

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Release build fails to compile | Missing the `releaseImplementation(...library-no-op...)` line. |
| Nothing is captured | `init()` never ran, or `enabled = false`. Check `AProxyI.isEnabled()`. |
| Notification never appears | `POST_NOTIFICATIONS` was denied on Android 13+, or no request has been made yet — the notification is posted from the request lifecycle, not from `init()`. Use `launch()` instead. |
| Auth headers missing from captures | The inspector interceptor runs before your auth interceptor. Add it last. |
| Requests stuck as "active" | A completion call is missing on some path. They now appear as `TIMED_OUT` after 60s — that entry tells you which call site to fix. |
| Tokens show as `**REDACTED**` | Working as intended. Redaction happens at capture and is irreversible. |
| A request you just made isn't listed | Capture is asynchronous; the UI refreshes itself via a listener. |
| Some traffic is invisible | Only traffic you route through the interceptor or the callback API is captured. WebView and native/NDK traffic is not. |

---

## How it works

### Debug-only by construction

The inspector captures `Authorization` headers, cookies, full bodies and
analytics payloads. In a consumer app that is addresses, phone numbers and order
data. Shipping that in release is a data-leak surface, so it is made
*structurally impossible* rather than gated on a runtime flag.

| Module | Wired via | Contains |
|---|---|---|
| `library` | `debugImplementation` | Real implementation: capture, storage, UI |
| `library-no-op` | `releaseImplementation` | Hollow stubs. No capture, no UI, no permission |
| `library-api` | transitive (both) | Inert data models shared by the two |

This is stronger than `if (BuildConfig.DEBUG)`, which inside a library resolves
against the *library's* `BuildConfig`, not your app's, and leaves the capture
code in the APK relying on R8 to strip it.

**Verified on every CI run** against the sample app's real APKs:

| | Debug | Release |
|---|---|---|
| `com.aproxyi.internal` classes | 54 | **0** |
| Inspector activities in merged manifest | 4 | **0** |
| `POST_NOTIFICATIONS` permission | yes | **no** |
| APK size | 6.5 MB | 2.9 MB |

### Redaction

Redaction happens **at capture and is irreversible** — secrets never enter the
in-memory store, so they cannot escape via the UI, share, or copy-as-cURL.

- **Headers**: `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`,
  `X-Api-Key`, `X-Auth-Token`, `X-Access-Token`, `X-Csrf-Token`
- **Query params and `params`**: names matching `key`, `sid`, `sig`, `otp`,
  `pin` exactly, or *containing* `token`, `secret`, `password`, `passwd`, `pwd`,
  `auth`, `signature`, `session`, `apikey`, `api_key`, `credential` — covering
  `access_token`, `oauth_token`, `client_secret` without listing every vendor.

**The tradeoff:** you cannot read a real token back to reproduce a 401 in curl.
That was chosen deliberately — a debug tool holding live credentials is one
screenshot away from leaking them. Log it yourself at the call site if you need
it.

### Capture is asynchronous

Completion handling runs on a single background thread, so a large payload never
blocks your caller. The consequence: a request is not in the store the instant
`onRequestSuccess` returns. This only matters if you read the store from code;
the UI refreshes from a listener.

`onRequestStart` does format the request body on the calling thread, but
pretty-printing is skipped above 64 KB so the cost stays bounded.

### Notifications

An ongoing notification shows live counts; tapping it opens the inspector, and
**Clear** empties the list. **You write no code for this**, including the
permission — on Android 13+ the library requests `POST_NOTIFICATIONS` itself the
first time you open the inspector. Denying it costs you only the shortcut, and
the resulting `SecurityException` is caught, so a denial can never crash your app.

Two limits: the notification appears only after the first request, and Clear
dismisses it until the next one. Both are why you want a manual `launch()` entry
point as the primary route.

---

## API surface and the parity contract

Everything under `com.aproxyi.internal.*` is Kotlin-`internal` and free
to change. The public surface is ~24 entry points across `AProxyI`,
`AnalyticsInspector`, `AProxyIWrapper`, the two interceptors, and the
models in `com.aproxyi.core`.

`library` and `library-no-op` must expose an **identical** public API — you link
one in debug and the other in release, so drift breaks your *release* build.
[Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
pins it, and CI additionally diffs the two `.api` files.

```bash
./gradlew apiDump    # after any public API change; commit the result
./gradlew apiCheck   # fails if code and *.api have diverged
```

---

## Building

```bash
./gradlew assembleDebug assembleRelease   # all modules
./gradlew testDebugUnitTest               # 35 unit tests
./gradlew apiCheck                        # public API unchanged
./gradlew :sample:assembleRelease         # proves both artifacts compile
./gradlew publishToMavenLocal             # install locally
```

Needs JDK 17+ and the Android SDK (compileSdk 35, build-tools 35.0.0):

```properties
# local.properties
sdk.dir=/path/to/android-sdk
```

```
library/           real implementation      → debugImplementation
  └ internal/      UI, notification, util   (Kotlin-internal)
library-no-op/     hollow stubs             → releaseImplementation
library-api/       shared inert models      (ships in both)
sample/            consumer app; exercises every public entry point
```

The sample is not decorative: it links `:library` in debug and `:library-no-op`
in release and calls every public method, so `:sample:assembleRelease` fails if
the two ever drift.

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for
the build commands, the API-parity rule that trips people up, and what a good
change looks like here.

## Status

**v0.1.0 — early, but verified.** Every push runs the full check on CI: build,
46 unit tests, lint, the public-API parity diff, and an assertion against the
real release APK that no capture code survives.

| | |
|---|---|
| Builds, debug + release | Yes |
| Unit tests | 46, including activity inflation under Robolectric |
| Lint | Clean |
| API parity enforced | Yes, in CI |
| Release stripping proven | Yes, against the real APK |
| Published to Maven Central | Not yet — `publishToMavenLocal` for now |
| **Run on a physical device** | **Not yet** |

That last row is the honest gap. The screens inflate, resolve every view id and
bind their adapters under Robolectric, but nobody has judged how they *look* on
real hardware. See [CHANGELOG.md](CHANGELOG.md) for what has landed.

## License

Apache 2.0 — see [LICENSE](LICENSE).
