# Contributing to AProxyI

Thanks for taking a look. This project is small and the rules are few, but one
of them is unusual — read the API parity section before touching public code.

## Getting set up

Needs JDK 17+ and the Android SDK (compileSdk 35, build-tools 35.0.0).

```properties
# local.properties
sdk.dir=/path/to/android-sdk
```

```bash
./gradlew assembleDebug assembleRelease
./gradlew testDebugUnitTest
```

## Before you open a pull request

Run what CI runs:

```bash
./gradlew assembleDebug assembleRelease testDebugUnitTest apiCheck \
          :library:lintRelease :sample:assembleRelease
```

## The API parity rule

This is the one that catches people.

`:library` and `:library-no-op` must expose an **identical** public API.
Consumers link the real one in debug and the hollow one in release, so any
difference breaks *their release build* — with an error that points nowhere near
the cause.

So, if you add or change a public member:

1. Mirror it in `:library-no-op`. Same name, same parameters, same defaults, same
   annotations. `inline` must stay `inline`: those bodies are baked into consumer
   bytecode, so a non-inline stub changes the call shape between variants.
2. Run `./gradlew apiDump` and commit the updated `*.api` files.
3. Confirm the two match:
   ```bash
   diff <(sort library/api/library.api) <(sort library-no-op/api/library-no-op.api)
   ```

CI runs that same diff. It has already caught drift that careful reading missed
— two suspend functions and a stray public `Companion`.

**The easiest way to avoid all of this is not to add public API.** Put new code
in `com.aproxyi.internal.*` and mark it `internal`. The no-op mirrors only what
is public, and the `internal` package is excluded from API validation.

## Things that will fail review

- **Capture code that can reach a release build.** The whole design rests on the
  release artifact containing nothing. If a change puts capture, storage or UI
  anywhere a release build can link it, it will not land.
- **Storing a credential unredacted.** Redaction happens at capture and is
  irreversible, deliberately. Do not add a path that stores a raw token "just for
  debugging".
- **Writing captures to disk.** In-memory only is a security decision, not an
  oversight.
- **An API call above `minSdk` without a guard.** `minSdk` is 21 and lint treats
  `NewApi` as an error. `Context.getColor` already shipped this bug once; use
  `ContextCompat`.
- **A public member added on one side only.** See above.

## Tests

- Pure logic — redaction, URL scoping, config — goes in `library-api`, plain JUnit.
- Anything needing a `Context`, `Log`, or resources goes in `library` with
  Robolectric.
- Touching a screen? Add to `ActivityInflationTest`, which inflates the real
  layouts. It is what catches a missing view id or a theme that cannot host
  Material components.

Completion handling is asynchronous, so call `AProxyI.awaitIdleForTesting()`
before asserting on stored state. `AProxyI.resetForTesting()` clears the
singletons between tests.

## Commit messages

Explain *why*, not just what. If you fixed a bug, say what the failure looked
like and what caused it. The git log is the design record here.
