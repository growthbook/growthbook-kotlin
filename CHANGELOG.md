# Changelog

All notable changes to the GrowthBook Kotlin SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [7.1.1] - 2026-04-*

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
