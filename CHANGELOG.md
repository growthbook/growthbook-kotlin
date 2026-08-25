# Changelog

All notable changes to the GrowthBook Kotlin SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---
## [7.8.0] - 2026-08-25

### Added
- Plugin system: `GrowthBookPlugin` interface for observing experiment and feature evaluations
- Built-in `GrowthBookTrackingPlugin` that batches events and POSTs them to the GrowthBook ingest endpoint
- `GBSDKBuilder.setPlugins()` to register plugins with the SDK
- `IGrowthBookSDK` interface extracted from `GrowthBookSDK` (`isOn`, `feature`, `suspendFeature`, `run`, `setAttributes`, `setAttributesSync`), so app code can depend on the abstraction and swap in a test double
- New `GrowthBookTest` module providing `FakeGrowthBook`, a deterministic in-memory `IGrowthBookSDK` for unit tests (no network or cache). Supports:
    - Feature overrides — `enable`/`disable`/`setValue`
    - Fixtures & scenarios — `setFeatures(Map)`, `copy()` to fork a base fixture per test, and `FakeGrowthBook.fromFeaturesJson(...)` to load a real dashboard export. `fromFeaturesJson` rejects encrypted, non-object and feature-less JSON with an explanatory error instead of an opaque decoding failure, and tells a bare map containing a flag named `features` apart from a features response
    - Honest `GBFeatureResult.source` — `override` for values set from code, `defaultValue` for seeded or loaded ones, `unknownFeature` for keys never configured — so code and hooks that branch on `source` (`setFeatureUsageCallback`, `GrowthBookPlugin`) are exercised against states production produces
    - Deterministic experiments — `setForcedVariation(key, index)`. Apart from skipping hashing, the returned `GBExperimentResult` matches what the real evaluator builds: the *variation* key, variation meta, and the same out-of-range fallback to the baseline with `inExperiment = false`
    - Interaction assertions — `wasQueried(id)`, `queriedFeatures()`


### Fixed
- `List<*>.toJsonElement()` in `:Core` now passes an already-serialized `JsonElement` through untouched instead of re-encoding it via `toString()`, matching `Map<*, *>.toJsonElement()`. This prevents double-encoding of nested list values when building request bodies
- Feature truthiness now matches the reference (TypeScript) SDK, whose `off = !value` is plain JS falsiness over the decoded value. A feature now evaluates as **off** (`on = false`) when its value is JSON `null`, the empty string (`""`), or zero in *any* numeric representation — `0.0`, `0.0f`, `0L`, `-0.0` and `NaN` included. Previously only `null`, `false` and integer `0` were off, because the zero check compared a boxed `Number` against `Int` `0`; a dashboard `defaultValue` of `0.0` was reported as `on` while every other SDK reported `off`. Values that are truthy in JS — the string `"0"`, `Infinity`, and empty arrays/objects — remain `on`
- An unresolvable feature value (`GBValue.Unknown`) is now **off** as well. It was reported as `on` even though a typed read via `featureValue<T>()` returns `null` for it
- `featureValue<V>(id)` no longer depends on the declared type of the receiver. The `GrowthBookSDK` member and the new `IGrowthBookSDK` extension now share one implementation, so switching a field from `GrowthBookSDK` to `IGrowthBookSDK` cannot change what a read returns (a member always shadows an extension in Kotlin, and the two bodies had diverged)
- `featureValue<V>(id)` dropped its hard-coded list of "supported" types, matching the reference (TypeScript) SDK's `getFeatureValue`, which applies no such gate. Requesting a supertype — `featureValue<Any>(id)` and friends — now returns the value instead of `null`. A type mismatch still returns `null`
- `featureValue<V>(id)` can now read array-valued features, returning them as `GBArray` (symmetric with `GBJson`) — or as `List<GBValue>`, which `GBArray` implements. Arrays previously returned `null` in every case, although the reference SDK's value type (`JSONValue`) includes `Array<JSONValue>`
- `:Core` now declares `kotlinx-coroutines-core` and `kotlinx-serialization-json` as `api` rather than `implementation` dependencies. Both types show up in its public API — `NetworkDispatcher.consumeSSEConnection` returns a `Flow`, while `TrackingNetworkDispatcher.consumePOSTRequest` and the public `toJsonElement()` helpers take/return `JsonElement` — but `implementation` publishes them into `runtimeElements` only. A consumer writing their own `NetworkDispatcher` or `TrackingNetworkDispatcher` therefore could not name those types without declaring kotlinx in their own build

### Changed
- **Behavioral change (spec conformance).** Because of the truthiness fix above, `isOn()` and
  `GBFeatureResult.on` now return `false` — where 7.7.0 returned `true` — for features whose
  resolved value is JSON `null`, `""`, a non-integer zero (`0.0`, `0L`, `-0.0`, `NaN`) or
  `GBValue.Unknown`. The flip only ever goes `on → off`, and it brings the Kotlin SDK in line
  with the reference (TypeScript) SDK; no dashboard change is involved.

  What to audit before upgrading:
    - Feature flags whose `defaultValue` (or any rule value) is `null`, `""` or a decimal zero,
      if the app uses `isOn()`/`on` as a "is this configured?" check rather than reading the value
    - Dashboards and metrics fed by `trackingCallback`, `setFeatureUsageCallback` or a
      `GrowthBookPlugin`: `GBFeatureResult.on`/`off` are reported through all three, so
      "share of users with flag on" can shift without any config change

  Targeting is unaffected: prerequisite rules evaluate the feature *value*, not `on`/`off`.
  Note that the shared cross-SDK spec (`cases.json`) only covers integer `0`, `null` and `false`,
  so a green spec run does not exercise these cases — see `GBFeatureTruthinessTests`

---
## [7.7.0] - 2026-08-24

### Changed
- Feature-flag and experiment targeting is significantly faster for large $in / $nin lists. Targeting conditions are now converted to the internal GBValue tree **once at
  feature load** instead of on every feature() / run() evaluation, and membership checks against arrays of 16+ items use a lazily-built HashSet (O(1) lookup) instead of a
  linear scan. On an internal payload with thousand-item $in targeting, isOn() dropped from ~2.2 ms to ~5 µs. Wire JSON, the public condition shape, and evaluation results are
  unchanged; case-insensitive operators (`$ini` / $nini / `$alli`) keep the existing fold-and-scan path

### Added
- `decodeAs<T>()` extension on `GBValue` (in `GrowthBookKotlinxSerialization`) to decode feature values into typed models via kotlinx.serialization. The default `Json` is tolerant of unknown keys, so feature config objects carrying fields the caller's model does not declare yet still decode successfully. Pass a custom `Json` to override (e.g. `Json { ignoreUnknownKeys = false }` for strict decoding).

### Fixed
- `GBArray` now implements value-based `equals`/`hashCode` (converted to a `data class`), so arrays with equal contents compare as equal.
- $in / $nin against a missing attribute no longer perform a membership lookup with a null value

---
## [7.6.0] - 2026-08-14

### Added
- Persistent feature-definition cache is now implemented on **every target** — previously only Android persisted a cache and the rest were no-ops. Apple (iOS/macOS) writes to `<Application Support>/GrowthBook-KMM/` via `NSFileManager`, the JVM to `<user.home>/.growthbook/GrowthBook-KMM/` (fallback `<java.io.tmpdir>`) via `java.io` — both atomic (temp file + rename) and self-healing on corrupt data — and JS and wasmJs to the browser `localStorage` under the `GrowthBook-KMM/` key namespace, self-healing on corrupt data and treating a disabled/unavailable `localStorage` (e.g. private browsing) as a cache miss rather than an initialization failure
- Remote-evaluation payloads are **not** persisted or served from the cache. The feature cache is keyed only by API key, so serving a remotely-evaluated payload could surface one user's evaluated features to the next after a logout/login on the same key; remote-eval therefore always fetches fresh from the network
- The `wasmJs` target is now configured for the browser (`browser()` instead of `nodejs()`) so it can persist through `localStorage`
- New `macosArm64` target for the `GrowthBook`, `Core`, and `GrowthBookKotlinxSerialization` artifacts
- `GBSDKBuilder.setCachingLayer(GBCachingLayer)` — provide your own cache implementation so GrowthBook persists its cached state through your own storage (e.g. a shared KMP key/value store or encrypted storage) instead of the built-in per-platform cache. Replaces both the feature-definition cache and sticky-bucket storage, and may be called in any order relative to the sticky-bucket setters

---
## [7.5.0] - 2026-08-14

### Added
- `GBSDKBuilder.setFeaturesChangeHandler()` — callback notified with a `GBFeaturesDiff` (added / removed / changed flags) on each refresh, so consumers can react to only the flags that changed instead of the whole feature set. Fires on all update paths (SSE / GET / remote-eval) after features are applied, and only when something changed

### Fixed
- SSE auto-refresh now emits decrypted features to the `Flow` for encrypted-feature projects, instead of a "success with empty data" event (the raw `features` field is null for encrypted payloads)
- An empty features payload (e.g. all flags deleted in the admin) is now applied as an empty feature set instead of surfacing a spurious refresh error

---
## [7.4.0] - 2026-08-14

### Added
- `GrowthBookSDK.updateAttributes()` / `updateAttributesSync()` — shallow-merge user
  attributes into the current map (parity with the TS SDK's `updateAttributes`): new
  keys are added, existing keys overwritten, untouched keys preserved. A `GBNull` value
  keeps the key with a null value (it is not removed); use `setAttributes()` to replace
  the whole map.

### Fixed
- Remote evaluation now re-runs when user attributes or forced features change:
  `setAttributes()`, `setAttributesSync()` and `setForcedFeatures()` were not triggering
  a fresh remote evaluation, so remote-eval consumers kept stale results after those
  changes.
- In remote-eval mode, `suspendFeature()`'s internal retry now goes through the
  remote-eval POST instead of a plain GET, so a retry can no longer momentarily surface
  non-personalized (unevaluated) feature definitions.
- Rapid attribute or forced-feature changes in remote-eval mode could apply an
  out-of-order (stale) evaluation when a slower earlier request completed after a newer
  one; responses from superseded remote-eval requests are now discarded. A caller
  awaiting such a superseded request is no longer reported a successful refresh, so
  `suspendFeature()` cannot return the older, stale evaluation — it re-joins the latest
  generation instead.
- `setForcedFeatures()` and `setAttributeOverrides()` values are now published atomically
  alongside the other evaluation inputs, so an evaluation running on another thread always
  observes the latest values as part of a single consistent snapshot.
- A custom `NetworkDispatcher` that throws synchronously while starting a request is now
  reported through the refresh handler as a fetch failure, instead of being swallowed or
  propagating out of `initialize()`/`setAttributes()`.
- Remote-eval POST body is now well-formed. User attributes and forced features were
  serialized via their `GBValue.toString()` (e.g. `"GBNumber(value=8490047)"`) instead of
  the underlying JSON value, so server-side targeting saw garbage; they are now encoded as
  real JSON. Forced features are also sent as an array of `[key, value]` pairs (matching
  the reference SDK) instead of a JSON object, which the GrowthBook proxy rejected with
  `400 Bad Request`.

---
## [7.3.0] - 2026-07-20

### Added
- `GBSDKBuilder.setCacheMaxAge()` — configurable cache freshness window; while the
  cache is younger than the given age, the next fetch is served from cache and the
  network call is skipped, provided the cached payload actually decodes to usable
  features/savedGroups — a fresh but empty/undecodable cache falls through to the
  network instead. `refreshCache()` always bypasses this window.
- `GrowthBookSDK.close()` — releases the instance's resources (stops any active SSE connection and cancels the background coroutine scope that processes fetched payloads). Call it when the SDK instance is no longer needed (e.g. on logout or before replacing it) to avoid leaking coroutines across repeated initializations

### Fixed
- Sticky-bucket race condition: evaluation could observe a torn mix of state (e.g. freshly fetched features together with stale sticky-bucket assignment docs) when a background refresh ran concurrently with `feature()`/`run()`. All cross-thread evaluation inputs (`features`, `attributes`, `forcedVariations`, `stickyBucketAssignmentDocs`, `stickyBucketIdentifierAttributes`, `savedGroups`) are now published together as a single immutable snapshot behind an atomic reference, so every evaluation reads one consistent view
- Sticky-bucket assignments generated during evaluation are now merged back into the context one key at a time (atomically) instead of writing the whole docs map back after `feature()`/`run()`. The previous whole-map write-back could overwrite assignments produced by a concurrent background refresh
- `savedGroups` passed to the `GrowthBookSDK` constructor were written to an unused private field and never reached evaluation; they are now stored on the context
- Encrypted feature payloads now decode before the sticky-bucket refresh, so sticky-bucket identifier attributes derive from the real features instead of an empty set on the first fetch (which could re-bucket users)

### Changed
- `suspendFeature()` now retries failed fetches with exponential backoff (capped)
  instead of an unbounded recursive loop, preventing DNS request flooding when the
  network is unavailable (#236). A hung network round (a dispatcher that connects but
  never responds) now times out after 30s and is treated as a failed attempt, so
  `suspendFeature()` can no longer hang indefinitely.
- Concurrent feature refreshes are now coalesced into a single shared in-flight
  request, so N parallel `suspendFeature()` callers no longer trigger N network
  fetches.
- The fetched payload (sticky-bucket refresh + feature application + `refreshHandler` invocation) is now processed on a defined background dispatcher (platform IO) instead of an arbitrary continuation thread. The `refreshHandler` callback is therefore invoked on a background thread — marshal back to your UI thread yourself if it touches UI state

### Breaking
- `GBContext` is no longer a `data class`. The compiler-generated `copy()`, `equals()`, `hashCode()` and `componentN()` (destructuring) members are no longer available. The primary constructor signature and all property accessors are unchanged, so normal construction and field access are unaffected

---

## [7.2.0] - 2026-06-12

### Added
- `GBSDKBuilder.setInitialFeatures()` — seed the SDK with a bundled fallback payload; features are applied immediately and the normal cache/network refresh still runs on top (network > disk cache > seed > code defaults)

### Fixed
- Cache write failure (disk full, I/O error) no longer discards a successfully fetched features payload; the write is now isolated in its own try/catch and its failure is logged but does not affect the current session
- Android cache write is now crash-safe: `fsync` is called before rename, and a `false` return from `renameTo` now throws `IOException` instead of silently leaving a stale cache file
- Concurrent SDK instances with the same `clientKey` no longer corrupt the shared cache file; `CachingAndroid` is now a singleton so its per-filename lock correctly serializes all writers
- Upgrading from 6.x to 7.x no longer silently discards the cached features on first launch (Android only — other platforms do not persist a disk cache); `FeatureCache.txt` is automatically migrated to `FeatureCache_<clientKey>.txt`. Apps using multiple SDK instances with different `clientKey`s may see one cold start on the first launch after upgrade — features self-correct after the first successful fetch

---

## [7.1.1] - 2026-04-23

### Fixed
fix: fire refreshHandler with success on 304 Not Modified response

### Added
Support for case-insensitive operators

---

## [7.1.0] - 2026-04-07

### Added
- New `featureValue` function
- Hide reified function from Objective-C

---

## [7.0.0] - 2026-03-27

### Added
- Scoped the feature cache key by clientKey (or API host) so each SDK instance uses its own isolated cache entry

### Fixed
- Correctly handle empty string attributes

---

## [6.1.5] - 2025-03-03

### Fixed
- Wrap `onFeatureUsage` and tracking callbacks in try-catch block to prevent crash in the SDK
- Fix prerequisite circular dependency

---

## [6.1.4] - 2025-02-13

### Fixed
- Fix `JsonDecodingException` by removing Accept Encoding header in NetworkDispatchers
- Synchronize `saveContent` and `getContent` in CachingAndroid
- Add Mutex to GBUtils to synchronize all sticky bucket read/write operations

---

## [6.1.3] - 2026-01-01

### Added
- `setAttributesSync()` — waits for sticky buckets to load before returning
- `setAttributeOverridesSync()` — synchronous version of attribute overrides
- `refreshStickyBucketsSync()` utility function
- ETag caching to NetworkDispatchers

### Removed
- `StickyBucketServiceHelper` internal class (no longer needed)

### Migration
Use sync methods for login/logout/user switching to prevent race conditions where experiments were evaluated before sticky buckets loaded.

---

## [6.1.2] - 2025-12-05

### Added
- `startAutoRefreshFeatures()` and `stopAutoRefreshFeatures()` for better handling SSE connection

---

## [6.1.1] - 2025-10-20

### Fixed
- Bug fix

---

## [6.1.0] - 2025-08-15

### Changed
- `GBStickyBucketService` methods changed to suspend
- `coroutineScope` added to `GBStickyBucketService`

---

## [6.0.0] - 2025-05-22

### Changed
- `hostURL` property renamed to `apiHost` to align with the TypeScript SDK
- `streamingHost` property added to differentiate streaming host URL from API host

---

## [5.0.0] - 2025-05-22

### Changed
- GB values moved to `:Core` module (used in `:GrowthBookKotlinxSerialization`)
- `forcedFeature` field of `GBFeatureEvaluator` is now a map of GB values

---

## [4.0.0] - 2025-03-03

### Changed
- `initialize()` changed from non-suspend to suspend method to eliminate null on first access

### Added
- `initializeWithoutWaitForCall()` for users not using coroutines

---

## [3.0.0] - 2025-01-27

### Changed
- User attributes type changed to map of GB values
- `attributesOverride` is now a map of GB values
- Forced features is now a map of GB values

---

## [2.0.0] - 2025-01-10

### Changed
- `value` field renamed to `gbValue`
- Type of `gbValue` changed to `GBValue`

### Added
- `inline fun <reified V>feature(id: String): V?`

---

## [1.1.63] - 2024-11-26

### Changed
- Type of `value` field of `GBFeatureResult` changed to `kotlinx.serialization.json.JsonElement`
