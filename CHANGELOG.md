# Changelog

All notable changes to the GrowthBook Kotlin SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---
## [7.4.0] - Unreleased

### Added
- `GrowthBookSDK.updateAttributes()` / `updateAttributesSync()` — shallow-merge user
  attributes into the current map (parity with the TS SDK's `updateAttributes`): new
  keys are added, existing keys overwritten, untouched keys preserved. A `GBNull` value
  keeps the key with a null value (it is not removed); use `setAttributes()` to replace
  the whole map.
- Background polling auto-refresh engine. `GBSDKBuilder.setRefreshInterval(<ms>)` configures a
  periodic network revalidation; start/stop it with `GrowthBookSDK.startPolling()` /
  `stopPolling()`. The poller runs as a coroutine on the SDK's background scope (not a dedicated
  thread), retries failed rounds with capped exponential backoff plus random jitter (so many
  instances that fail together do not all retry in lockstep), and is mutually exclusive with SSE —
  starting SSE stops the poller and `startPolling()` is a no-op while SSE is active; the switch
  between the two mechanisms is race-free. A round that throws is treated as a failed round (logged +
  backoff) rather than terminating the loop. Disabled by default; intended mainly for long-lived
  JVM/backend usage (tie it to app lifecycle on mobile).
- `GBSDKBuilder.setStaleTtl(<ms>)` — turns `setCacheMaxAge()` into a full three-tier
  stale-while-revalidate policy: `age < staleTtl` → fresh (served, network skipped);
  `staleTtl ≤ age < cacheMaxAge` → stale (served immediately + background revalidation);
  `age ≥ cacheMaxAge` → expired (not served, refetched as a cache miss). The hard ceiling (third
  tier) is armed only when `staleTtl` is set; `setCacheMaxAge()` used alone keeps its original
  two-tier behaviour (serve-stale beyond the window, never dropped), so existing consumers are
  unaffected. Set `staleTtl < cacheMaxAge`.
- `GBSDKBuilder.setServeStaleOnError(<Boolean>)` — HTTP `stale-if-error` semantics for the expired
  (third) tier: when enabled, a cache older than `cacheMaxAge` is served as a last resort **only if**
  the revalidating network round fails, so an offline client keeps its stale flags instead of falling
  back to code defaults. Default false fails closed (nothing stale served past the ceiling). The
  freshness ceiling still holds whenever the network is reachable.

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

### Changed
- Exponential backoff is now centralised in `BackoffPolicy` (`:Core`) and shared by the polling
  engine and `suspendFeature()`'s retry loop. Behaviour of `suspendFeature()` is unchanged
  (initial 1s, doubling, 60s cap, 5 attempts).
- A `GBCacheRefreshHandler` that throws is now caught and logged instead of propagating. The SDK's
  background payload-processing scope also carries a `CoroutineExceptionHandler`, so an exception
  escaping a fire-and-forget fetch (e.g. from a consumer handler) is logged rather than reaching the
  platform's default uncaught-exception handler — which on Android crashes the app. This matters most
  under polling, where the fetch path runs repeatedly.

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
