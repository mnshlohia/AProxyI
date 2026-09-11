# Known issues

Open defects in the current implementation, found by source review. None are
fixed yet. Ordered by severity.

Line numbers refer to `library/src/main/kotlin/com/networkinspector/`.

## Crash and correctness

**1. `onRequestCancelled` is not crash-safe** — `NetworkInspector.kt:299-321`

The only public entry point with no `try/catch`. Every sibling wraps its whole
body in `catch (e: Throwable)`, and the class KDoc advertises crash-safety.

**2. `addRequest()` has a race that can throw** — `NetworkInspector.kt:447-454`

```kotlin
requests.add(0, request)
while (requests.size > config.maxRequests) {
    requests.removeAt(requests.size - 1)
}
```

On a `CopyOnWriteArrayList`, reachable from both the single-thread executor
(success/failed) and the caller thread (cancelled). Two threads can both pass the
size check, and `removeAt` on a stale index throws `IndexOutOfBoundsException`.

`AnalyticsInspector.addEvent` has the identical shape but is wrapped in
`catch (Throwable)`, so there it silently drops events instead of crashing.

**3. Stat counters are not thread-safe** — `NetworkInspector.kt:59-62`

`totalRequests`, `successfulRequests`, `failedRequests` and `activeRequestCount`
are plain non-`volatile` `Int`s, mutated from OkHttp dispatcher threads, the
executor thread and caller threads. Lost updates and stale reads. Use
`AtomicInteger`.

## Leaks

**4. `activeRequests` entries never expire**

A request whose completion callback is never invoked — an early `return`, a
swallowed exception, an abandoned coroutine — leaks its map entry *and*
permanently inflates `activeRequestCount`, so the notification shows a
forever-climbing "N active". Much more likely on the callback API than via the
interceptor, since the caller owns the completion call. Needs a TTL sweep or cap.

**5. `clearAll()` is incomplete** — `NetworkInspector.kt:371-381`

Clears `requests` and zeroes the three totals, but leaves `activeRequests`
populated and `activeRequestCount` untouched, so the active count survives a
Clear.

## Security and privacy

These are the highest product risk: the library exists to display exactly the
data that must not escape a debug build.

**6. No redaction on the callback path**

`onRequestStart(headers = ...)` stores headers verbatim. Redaction exists only in
`NetworkInspectorInterceptor`, and only for `Authorization`, `Cookie` and
`Set-Cookie`. The client-agnostic callback API is this library's differentiator,
so the path being promoted is the leakier one.

**7. URL query strings are never redacted**

`?access_token=`, `?api_key=`, `?sid=` are stored and rendered in full.
`shortName`, `host` and `path` all derive from the raw URL, and the copy/share
and copy-as-cURL actions export it.

**8. `AnalyticsInspector.enabled` is unwired** — `AnalyticsInspector.kt:25`

Defaults to `true`, and `NetworkInspector.init()` never references
`AnalyticsInspector`. Passing `NetworkInspectorConfig.RELEASE` disables network
capture while analytics capture keeps running. Two independent switches, one
undocumented.

## Performance and ANR

**9. `onRequestStart` formats the body on the calling thread** —
`NetworkInspector.kt:139`

`BodyFormatter.format(body, config.maxBodySize)` is a Gson pretty-print of up to
200 KB, run synchronously — while the success and failure paths deliberately
offload the same work to a background executor "to avoid ANR". On the callback
API that caller is frequently the main thread.

**10. Double formatting** — `CallbackInterceptor.create()`

Calls `BodyFormatter.format(body)`, then passes the formatted string into
`onRequestStart`, which formats it again.

**11. `shouldTrack()` recompiles regexes per request** —
`core/NetworkInspectorConfig.kt`

`Regex(pattern, IGNORE_CASE)` is constructed inside the loop, for every pattern,
on every call. Precompile into the config object.

**12. Two full array copies per recorded request**

`CopyOnWriteArrayList.add(0, …)` copies the backing array, and each trim
`removeAt` copies it again — up to `maxRequests` (500) elements each time.

## API and design

**13. `excludedHosts` and `excludedPaths` are the same filter**

Both run `url.contains(Regex(pattern))` against the whole URL. Neither scopes to
host or path as the names promise.

**14. Two competing body-size limits**

`config.maxBodySize = 200_000` (`Int`, characters) versus
`NetworkInspectorInterceptor.maxContentLength = 250_000L` (`Long`, bytes).
Different units, different defaults, no documented precedence.

**15. `init()` is not idempotent**

Calling it twice replaces the config and constructs a second
`InspectorNotificationManager`, re-creating the channel.

**16. `appContext` is dead** — assigned at `NetworkInspector.kt:94`, never read.

**17. `notifyListeners()` catches `Exception`** while the rest of the file
catches `Throwable`.

## Not a defect

Listener callbacks fire on a background thread, but
`RequestListActivity.onRequestsUpdated` wraps its work in `runOnUiThread`, so the
UI path is correct.

## Suggested order

Items 1-3 and 6-8 are worth fixing before anyone else integrates: crashes, a
permanently wrong "active" count, and credential leakage on the path the project
promotes. 9-12 matter once a busy screen is under test. 13-17 are cleanups.

Items 6 (redaction policy) and 4 (eviction strategy) need a product decision, not
just a patch.
