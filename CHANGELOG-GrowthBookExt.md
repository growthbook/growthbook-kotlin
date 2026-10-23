# Changelog — GrowthBookExt

All notable changes to the `GrowthBookExt` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [4.0.0] - Unreleased

Catch-up release: the module had drifted behind the core SDK, which is versioned and released
separately. Everything here is either that catch-up or the interface work that unblocks it.

### Breaking changes
- Every helper now takes `IGrowthBookSDK` as its extension receiver instead of the concrete
  `GrowthBookSDK` — `Extensions.kt`, `Flag.kt`, `Delegates.kt` and `AttributesDsl.kt`. Source-
  compatible for existing call sites (`GrowthBookSDK` is an `IGrowthBookSDK`), but binary-breaking:
  an extension's receiver is its first JVM parameter, so the signatures changed. Recompile against
  this version rather than mixing artifacts.

  What it buys: the helpers now work against any implementation of the interface, including
  `FakeGrowthBook` from the `GrowthBookTest` artifact. Previously a test double could not be used
  with them at all.

  `growthBook { }` deliberately still returns `GrowthBookSDK`: a factory should hand back the most
  specific type, so callers keep `refreshCache()`, `startPolling()` and the rest.

### Added
- Configuration DSL: `initialPayload`, `fetchStatsHandler`, `refreshInterval`, `staleTtl` and
  `serveStaleOnError`, all of which existed on `GBSDKBuilder` but had no DSL counterpart.
- Awaiting accessors for the startup window, over the core's `suspendFeature`: `awaitEnabled(id)`,
  `awaitEnabled(id, fallback)`, `awaitBoolean`, `awaitString`, `awaitInt`, `awaitLong`,
  `awaitDouble`, `awaitJson`, and `await(flag)` for typed flags. Until the first payload is applied
  every feature is unknown, so a synchronous read at startup returns the default; these suspend
  until definitions are available and only then evaluate.

  They are not a guarantee of fresh data — when every fetch attempt fails, the underlying
  `suspendFeature` gives up and evaluates whatever is loaded. Property delegates have no awaiting
  form: `ReadOnlyProperty.getValue` is not `suspend`, so a property cannot wait.

  Named `await*` rather than overloading `get*` because `suspend` takes no part in signature
  resolution — same-name overloads would conflict.

### Fixed
- `cacheMaxAge` KDoc described a plain "skip the fetch" window. It has been a three-tier
  stale-while-revalidate policy since core 7.9.0; the documentation now matches, and cross-references
  `staleTtl` and `serveStaleOnError`.

### Internal
- New `ConfigDslCoverageTest` reflects over `GBSDKBuilder` and fails when a public `set*` has no DSL
  counterpart (or when the coverage map names a setter that no longer exists). This drift was found
  by hand after five settings had accumulated; the next one fails in CI instead.

---

## [3.0.0] - 2026-10-16

`GrowthBookExt`'s own API only gains below. The major bump propagates the breaking changes in
`GrowthBook` 9.0.0, which this artifact exposes as an `api` dependency — upgrading here pulls the
new core in transitively, including the silent change to how feature usage is reported. See the
[core changelog](CHANGELOG.md#900---unreleased).

### Added
- `eventLogger` in the configuration DSL, mirroring `GBSDKBuilder.setEventLogger`. Receives explicit
  `GrowthBookSDK.logEvent` calls; see the [core changelog](CHANGELOG.md#900---unreleased).

  ```kotlin
  val sdk = growthBook {
      apiKey = "sdk-abc"
      networkDispatcher = GBNetworkDispatcherKtor()
      eventLogger = { eventName, properties, _ -> analytics.track(eventName, properties) }
  }
  ```

---

## [2.0.0] - 2026-08-26

No changes to `GrowthBookExt` itself. The major bump propagates the breaking changes in
`GrowthBook` 8.0.0, which this artifact exposes as an `api` dependency — upgrading here pulls the
new core in transitively. See the [core changelog](CHANGELOG.md#800---2026-08-26).

---

## [1.0.0] - 2026-08-25

Initial release of `GrowthBookExt` — a pure-Kotlin companion module with
quality-of-life helpers over the core SDK. No extra runtime dependencies; all
Kotlin Multiplatform targets.

### Added
- Typed feature accessors on `GrowthBookSDK` for `String`/`Boolean`/`Int`/`Long`/`Float`/`Double`,
  each in three variants: `getX(id, default)`, `getXOrNull(id)`, `getXOrElse(id) { ... }`,
  plus `getJson(id)`. Boolean helpers `isEnabled(id)`, `isDisabled(id)` and
  `isFeatureKnown(id)` (distinguishes "missing" from "present but off").
- `FallbackStrategy` (`FAIL_OPEN` / `FAIL_CLOSED`) with `isEnabled(id, fallback)` —
  an explicit fail-open/fail-closed policy that applies only to a feature absent from
  the loaded configuration. A loaded feature whose evaluation fails keeps its real
  evaluated value, so an evaluation error cannot make `FAIL_OPEN` turn a flag on.
- Typed flags: `Flag<T>` (key + type + per-feature default) with `value(flag)` and
  `isOn(flag)`. Supported types `Boolean`/`String`/`Int`/`Long`/`Float`/`Double`;
  numeric flags are robust to how the number was stored.
- Property delegates: `featureFlag(key)`, `featureFlag(key, fallback)` and
  `featureFlag(flag)` — read a flag as a Kotlin property with `by`. The flag is
  re-evaluated on every read, so the property always reflects the current config.
- Attributes DSL: `setAttributes { }` / `buildAttributes { }` with an `obj { }` block
  for nested objects, hiding the `GBValue` wrappers.
- Configuration DSL: `growthBook { }` — assemble and initialize the SDK declaratively,
  covering the full `GBSDKBuilder` surface, including `plugins`, `cachingEnabled`,
  `cacheMaxAge`, `cachingLayer`, `featuresChangeHandler`, and sticky bucketing via
  either `stickyBucketService` or `stickyBucketScope` (+ optional `stickyBucketPrefix`).

---
