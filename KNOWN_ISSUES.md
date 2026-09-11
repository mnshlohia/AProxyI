# Known issues

Defects found by source review. Line numbers refer to
`library/src/main/kotlin/com/networkinspector/` unless stated otherwise.

**Status: 14 of 17 fixed. 3 remain open.** Nothing below has been compiled or
run — there is no Android SDK in the environment the fixes were written in.

---

## Open

**12. Two full array copies per recorded request**

`requests` is a `CopyOnWriteArrayList`; `add(0, …)` copies the backing array and
each trim `removeAt` copies it again — up to `maxRequests` (500) elements each
time. The insert and trim are now serialised under `synchronized(requests)`, so
this is no longer a crash risk, only wasted work. Fixing it properly means
swapping the data structure (an `ArrayDeque` behind the same lock), which is a
larger change than the crash fix needed.

**13. `excludedHosts` and `excludedPaths` are the same filter**

Both run their patterns against the whole URL, so neither scopes to host or path
as the names promise. Left alone deliberately: tightening the semantics would
silently change which requests existing integrators capture. Needs a decision —
fix the behaviour and document a breaking change, or rename the fields to match
what they actually do.

**14. Two competing body-size limits**

`config.maxBodySize = 200_000` (`Int`, characters) versus
`NetworkInspectorInterceptor.maxContentLength = 250_000L` (`Long`, bytes).
Different units, different defaults, no documented precedence. Needs an API
decision on which wins.

---

## Fixed

### Crash and correctness

**1. `onRequestCancelled` was not crash-safe** — now wrapped in
`catch (Throwable)` like every sibling entry point, matching the class's
documented crash-safety contract.

**2. `addRequest()` race** — insert and trim now run under
`synchronized(requests)`. Previously reachable from the worker thread
(success/failed) and caller threads (cancelled), where two threads could both
pass the size check and `removeAt` a stale index, throwing
`IndexOutOfBoundsException` out of a `CopyOnWriteArrayList`.

**3. Stat counters were not thread-safe** — `totalRequests`,
`successfulRequests`, `failedRequests` and `activeRequestCount` are now
`AtomicInteger`. They are mutated from OkHttp dispatcher threads, the worker
thread and caller threads, so plain `Int`s lost updates and read stale.

### Leaks

**4. `activeRequests` entries never expired** — added a TTL sweep. In-flight
requests older than `config.activeRequestTimeoutMs` (default 60s) are moved to
the new `RequestStatus.TIMED_OUT` and counted as failed. The sweep runs on the
request path, throttled to at most once a second and guarded by a CAS so only
one thread sweeps. Set the timeout to `0` to disable.

A missed completion call now shows up as a visible `TIMED_OUT` entry instead of
silently inflating the active count forever.

**5. `clearAll()` was incomplete** — now also clears `activeRequests` and resets
`activeRequestCount`, so the active count no longer survives a Clear.

### Security and privacy

Redaction happens **at capture, irreversibly**: sensitive values never enter the
in-memory store, so they cannot escape through the UI, share, or copy-as-cURL.
The cost is that a real token is not recoverable when debugging a 401 — that
tradeoff was chosen deliberately.

**6. No redaction on the callback path** — redaction moved into
`NetworkInspectorConfig` and is now applied centrally in
`NetworkInspector.onRequestStart` and `onRequestSuccess`, so it covers the
callback API and the interceptor alike. Default headers: `Authorization`,
`Proxy-Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key`, `X-Auth-Token`,
`X-Access-Token`, `X-Csrf-Token`. Configurable via `redactedHeaders`.

**7. URL query strings were never redacted** — `config.redactUrl()` now rewrites
sensitive query values, preserving path and fragment. A parameter is redacted
when its name matches `redactedQueryParams` exactly (`key`, `sid`, `sig`, `otp`,
`pin`) or *contains* any of `token`, `secret`, `password`, `passwd`, `pwd`,
`auth`, `signature`, `session`, `apikey`, `api_key`, `credential` — which catches
`access_token`, `oauth_token` and `client_secret` without enumerating vendors.
Params passed separately via `onRequestStart(params = …)` get the same treatment.

**8. `AnalyticsInspector.enabled` was wired to nothing** — `NetworkInspector.init`
now calls `AnalyticsInspector.setEnabled(config.enabled)` and
`setLogToLogcat(config.logToLogcat)`. Previously a `RELEASE` config silenced
network capture while analytics capture kept running.

### Performance and ANR

**9. Body formatting on the calling thread** — *partially* addressed.
`BodyFormatter.formatString` now skips pretty-printing above a 64 KB input
(`PRETTY_PRINT_LIMIT`). This matters because `tryFormatJson` parsed and
re-serialised the **entire** payload and only truncated afterwards, so
`maxBodySize` never bounded the work.

The threading asymmetry remains: `onRequestStart` still formats synchronously
while the completion paths offload to the worker thread. Moving it off-thread
was rejected for now — the in-flight record would have to carry the raw body
until completion, and a fast request could complete before formatting ran,
losing the body entirely. The size bound removes the practical hazard.

**10. Double formatting** — `CallbackInterceptor.create()` now passes the raw
body to `onRequestStart` instead of formatting it first and formatting again
inside.

**11. `shouldTrack()` recompiled regexes per request** — patterns are now
compiled once per config via `by lazy`. An invalid pattern is dropped rather
than thrown, so a bad exclusion cannot break capture.

### API and design

**15. `init()` was not idempotent** — now guarded, so a second call logs a
warning and returns instead of replacing the config and constructing a second
`InspectorNotificationManager`.

**16. `appContext` was dead** — assigned but never read; now used to construct
the notification manager.

**17. `notifyListeners()` caught `Exception`** — now catches `Throwable`, matching
the rest of the file.

---

## Not a defect

Listener callbacks fire on a background thread, but
`RequestListActivity.onRequestsUpdated` wraps its work in `runOnUiThread`, so the
UI path is correct.
