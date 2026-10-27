# Changelog — GrowthBookExt

All notable changes to the `GrowthBookExt` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [3.1.0] - Unreleased

### Added
- `GrowthBookSDK.featureRefreshFlow(): Flow<GBFeatureRefreshEvent>` — the feature refresh listeners
  added in `GrowthBook` 9.1.0, exposed as a flow so the subscription follows the collecting
  coroutine instead of a handle you have to cancel by hand.
    - Cold and per-collector: each collection registers its own listener and removes it when the
      coroutine ends, however it ends. Nothing is replayed, and collection necessarily starts after
      `initialize()` has applied the cached payload, so the cold-start load never appears in the
      stream — read `getFeatures()` for the state at the time collection starts, or register on the
      builder (`GBSDKBuilder.addFeatureRefreshListener`) when that load must be observed.
    - Events are emitted on whichever thread the SDK reports them from, without a context switch,
      so add your own `flowOn` if a collector must run elsewhere. A slow collector cannot block a
      refresh: the producer only offers, and the flow buffers a single event and drops the one it
      overtakes, since an event describing superseded definitions is of no use.
    - Collecting an SDK that is already closed completes immediately. Closing one *while* a
      collector is attached does not end the flow — the SDK has no termination signal to deliver —
      so scope the collection rather than relying on it to complete on its own.
    - Hidden from the Objective-C header (`Flow` has no usable representation there); iOS consumers
      use `addFeatureRefreshListener` directly.

- `growthBook { }` gained `featureRefreshListeners`, mapping onto
  `GBSDKBuilder.addFeatureRefreshListener` so DSL users can also register before the first load.

### Changed
- `growthBook { cachingEnabled = false }` now does what its KDoc always said: the SDK neither reads nor
  writes the feature cache. Before, only the write was skipped, and a cache file left by an earlier run
  was still applied on a cold start. **This is a silent behaviour change** carried in from the core: with
  caching off, a cold offline start now gets `initialFeatures` or code defaults rather than that file.
  See the [core changelog](CHANGELOG.md#1000---unreleased).

Requires `GrowthBook` 9.1.0, which this artifact pulls in transitively.

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
