# Security

AProxyI displays live credentials, cookies, request and response bodies, and
analytics payloads. Its security properties are load-bearing, so they are worth
stating plainly.

## Design guarantees

**A release build contains no capture code.** Consumers link `:library` in debug
and `:library-no-op` in release. The release artifact is hollow stubs — no
capture, no storage, no UI, and no `POST_NOTIFICATIONS` permission or activity
merged into the host manifest. CI verifies this on every push by pulling apart
the sample app's real release APK.

**Nothing is written to disk.** Captures live in a bounded in-memory store and
die with the process. This is why AProxyI does not persist across process death,
and it is deliberate.

**Redaction happens at capture and is irreversible.** Secrets never enter the
store, so they cannot escape via the UI, the share action, or copy-as-cURL. The
cost is that you cannot read a real token back to reproduce a 401; that trade
was made knowingly.

**The UI is not reachable by other apps.** Every inspector activity is
`exported="false"`, and the notification is posted with
`VISIBILITY_SECRET` and `setLocalOnly`.

## What it does not protect against

- Anyone with physical access to an unlocked device running a **debug** build
  can read whatever traffic has been captured. That is the tool's purpose.
- Redaction is name-based. A credential in a field whose name matches none of
  the configured patterns will be stored in full. Extend `redactedHeaders` and
  `redactedQueryParams` for your own conventions.
- A credential inside a request or response **body** is not redacted.

## Reporting a vulnerability

Open a [security advisory](https://github.com/mnshlohia/AProxyI/security/advisories/new)
rather than a public issue. Please include the version, an outline of the
impact, and a reproduction if you have one.
