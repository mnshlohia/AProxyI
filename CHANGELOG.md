# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Nothing yet.

## [0.1.0] — 2026-09-11

First working version. Not yet published to Maven Central.

### Added

- On-device inspection of network requests and responses, with headers, bodies,
  timings, sizes, pretty-printed JSON, copy-as-cURL, search and status filters.
- Analytics event capture for Firebase, CleverTap, AppsFlyer and Facebook, on
  its own screen.
- Client-agnostic callback API (`onRequestStart` / `onRequestSuccess` /
  `onRequestFailed` / `onRequestCancelled`), plus an optional OkHttp
  interceptor, a fluent wrapper with coroutine support, and a callback adapter.
- Debug-only by construction: `:library` for `debugImplementation`,
  `:library-no-op` for `releaseImplementation`, sharing inert models in
  `:library-api`. A release build contains no capture code at all.
- Capture-time, irreversible redaction of eight credential headers and
  token-shaped query parameters, applied on every capture path.
- A TTL sweep that moves requests with no completion call into `TIMED_OUT`,
  rather than letting them inflate the active count forever.
- The library requests `POST_NOTIFICATIONS` itself on Android 13+, so
  integrators do not have to.
- 46 unit tests, including activity inflation against the real layouts under
  Robolectric.
- CI running build, tests, lint, public-API validation, a parity diff between
  the two artifacts, and an assertion against the real release APK.
- `maven-publish` for all three modules with sources jars and POM metadata.

### Fixed

Defects found and fixed while getting to this release:

- `Context.getColor` (API 23) called against `minSdk` 21 — crashed on Android 5.
- Inspector activities pinned no theme, so they inherited the host app's and
  could fail to inflate Material components.
- Redaction existed only on the OkHttp path; the callback API stored headers
  verbatim and URLs were never redacted anywhere.
- `AnalyticsInspector` had its own enable flag wired to nothing, so a `RELEASE`
  config silenced network capture while analytics kept recording.
- Stat counters were plain `Int`s mutated from several threads.
- The request-list insert-and-trim could throw `IndexOutOfBoundsException`.
- A missed completion call leaked its entry and permanently inflated the active
  count; `clearAll()` did not reset in-flight state.
- `onRequestCancelled` was the one entry point with no `try/catch`, in a class
  documented as crash-safe.
- Body formatting parsed and re-serialised the whole payload before truncating,
  so `maxBodySize` never bounded the work.
- `excludedHosts` and `excludedPaths` both matched against the entire URL.
- Two competing body-size limits, in different units.
- `init()` was not idempotent.

[Unreleased]: https://github.com/mnshlohia/AProxyI/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/mnshlohia/AProxyI/releases/tag/v0.1.0
