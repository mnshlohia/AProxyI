## What this changes

<!-- And why. If it fixes a bug, what did the failure look like? -->

## Checks

- [ ] `./gradlew assembleDebug assembleRelease testDebugUnitTest apiCheck :library:lintRelease :sample:assembleRelease`
- [ ] Added or updated tests
- [ ] No capture code, storage or UI can reach a release build
- [ ] No credential is stored unredacted

## Public API

- [ ] This change adds no public API

If it does, all of these must be true:

- [ ] Mirrored in `:library-no-op` — same signature, defaults, annotations, and `inline` where the real one is
- [ ] `./gradlew apiDump` run and the `*.api` files committed
- [ ] `diff <(sort library/api/library.api) <(sort library-no-op/api/library-no-op.api)` is empty
