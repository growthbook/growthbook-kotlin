# Changelog — GrowthBookCaffeine

All notable changes to the `GrowthBookCaffeine` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-09-17

Initial release of `GrowthBookCaffeine` — optional [Caffeine](https://github.com/ben-manes/caffeine)-backed
adapters for the core SDK. JVM only.

### Added
- `GBCaffeineCachingLayer` — a `GBCachingLayer` over a bounded in-memory Caffeine cache, for
  servers that want the feature cache off disk and under a memory bound. Configurable by entry
  count (`maximumSize`) or by the total UTF-8 size of the cached payloads (`maximumWeightBytes`),
  with optional `expireAfterWrite` / `expireAfterAccess`, `recordStats` and an injectable `ticker`.
  Adds `clear()`, `cleanUp()` and `stats()` on top of the interface.
- `GBCaffeineStickyBucketService` — a `GBStickyBucketService` holding assignments in a separate
  bounded cache, so a server stops writing a file per user on the evaluation path. Same knobs, plus
  `clear()` for user-switch and sign-out flows. Takes the API key: its keys
  (`attributeName||attributeValue`) do not carry one, so they are namespaced
  `gbStickyBuckets__<apiKey>_` exactly as the SDK's default service is. One service per SDK
  instance.
- `GBCaffeineCacheStats` — an immutable counter snapshot, so the Caffeine version moving does not
  change what this module reports.

### Notes
- **Expiry is eviction, not freshness.** `expireAfterWrite` bounds how long a payload is kept, not
  how old the SDK will let it get — that remains `setCacheMaxAge` / `setStaleTtl`, evaluated
  against the payload's own timestamp. An evicted entry is a cache miss and the SDK refetches.
- **The caching layer serves feature caches only.** `GBSDKBuilder.setCachingLayer` also backs the
  *default* sticky bucket service; per-user documents sharing the feature payload's size bound
  would be evicted by it and silently rebucket users. Any other key is dropped and reported once
  through `onError` as a `GBCaffeineCacheScopeException`, or through `java.util.logging` at
  `WARNING` when no callback was given — pass `GBCaffeineStickyBucketService` to
  `setStickyBucketService(...)` instead.
- `clear()` on either adapter drops stored state but does not by itself change the next evaluation,
  which runs against the features and assignment documents held in the SDK's context. Follow it
  with `setAttributes` / `setAttributesSync` to make the SDK reload assignments.
- Assignments and cached payloads are per-process: a restarted or additional instance starts cold.
  Use a shared, durable store when they must survive a restart or be seen by every instance.
- Built with `jvmToolchain(17)`, so the published bytecode is Java 17 (class 61) rather than
  whatever JDK built it — matching the core SDK's JVM artifact, which is the floor a consumer of
  this module meets anyway. Caffeine 3.x accordingly, the maintained line.
