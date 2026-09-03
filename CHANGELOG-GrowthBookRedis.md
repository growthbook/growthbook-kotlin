# Changelog — GrowthBookRedis

All notable changes to the `GrowthBookRedis` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-08-31

Initial release of `GrowthBookRedis` — Redis-backed sticky bucketing and feature
cache for server-side JVM deployments, so a horizontally scaled fleet shares one
store instead of one per instance. Kotlin/JVM only (not multiplatform); both Redis
clients are `compileOnly`, so the artifact adds no transitive client dependency.

### Added
- `GBRedisStickyBucketService` — a `GBStickyBucketService` storing one JSON document
  per user under `<keyPrefix><attributeName>||<attributeValue>`, with no TTL.
  `getAllAssignments` fetches every attribute in a single `MGET` rather than one
  `GET` per attribute. The default (empty) key prefix matches the TypeScript SDK's
  `RedisStickyBucketService`, so the two can share one Redis.
- `GBRedisCachingLayer` — a `GBCachingLayer` over Redis, scoped to the feature cache of the
  client key it is constructed with. Because `GBCachingLayer` is synchronous and read inside
  the SDK's non-suspending `initialize()`, reads are served from memory: `warmUp()` loads
  Redis in one round trip beforehand, `getContent` never blocks, and `saveContent` updates
  memory and pushes to Redis in the background. Unlike the sticky bucket layout this one has no
  counterpart in another GrowthBook SDK — the payload is stored as this SDK writes it, with its
  own `cachedAt` inside.
- `GBRedisCacheScopeException` — reported through `onError` when the caching layer is asked for
  a key outside its feature cache, which in practice means it has been left to back the
  *default* sticky bucket service. It cannot: sticky documents are keyed per user and read
  synchronously, so they can never be warmed up, and the assignments would be written to Redis
  and never read back. Such keys reach neither Redis nor memory, so the mistake is reported
  rather than silently rebucketing every user on restart. Pass `GBRedisStickyBucketService` to
  `setStickyBucketService(...)` — an explicit service takes precedence over the caching layer,
  and the two are designed to be used together.
- `GBRedisCommands` — the three-method (`get`/`set`/`mget`) suspending seam both of the
  above are built on. Implement it to plug in a client this module ships no adapter for.
  `set` takes the expiry (`ttlSeconds: Long?`, `null` for none) so a custom implementation
  never silently drops it.
- Optional `ttl: Duration?` on both the sticky bucket service and the caching layer, applied as
  `SET … EX` in one round trip. `null` (the default) means no expiry — matching the TypeScript
  Redis service, where an assignment must outlive any single process. Set one where identifiers are
  anonymous or high-cardinality and unbounded growth matters; a returning user past the TTL is
  rebucketed, and in a fleet shared with the TypeScript SDK its expiry-less `SET` clears it.
  Validated at construction: below one second is rejected, since Redis rejects `EX 0`.
- `GBJedisRedisCommands` — adapter over Jedis. Takes `UnifiedJedis`, the common supertype
  of every Jedis topology (`RedisClient`, `RedisClusterClient`, `JedisCluster`, and
  `JedisPooled` on older versions), rather than the deprecated `JedisPool`. Jedis blocks,
  so commands run on an injectable dispatcher (`Dispatchers.IO` by default).
- `GBLettuceRedisCommands` — adapter over Lettuce. Uses `connection.async()` and awaits
  the future, so no thread is blocked and no dispatcher is needed.
- `GBRedisStickyBucketService.jedis(...)` / `.lettuce(...)` and `GBRedisCachingLayer.jedis(...)` /
  `.lettuce(...)` factories, so the common case is a single call. They live in
  `com.sdk.growthbook.redis`, one file per client, so no class carries both `UnifiedJedis` and
  Lettuce's `StatefulRedisConnection` in its signatures — harmless under HotSpot's lazy
  resolution, but eager analysers such as GraalVM native-image would trip over the absent
  `compileOnly` client. Import the factory alongside the class.

### Notes
- **Failures are fail-open.** Redis errors and malformed payloads degrade to a cache or
  assignment miss and are reported to an optional `onError` callback instead of being
  thrown: assignments are read on the evaluation path and written from a fire-and-forget
  coroutine, where an exception would cancel the caller's scope. `CancellationException`
  is always rethrown. The callback itself is isolated too — a consumer callback that throws
  is swallowed rather than allowed to escape and cancel the very scope fail-open protects.
- **Cache writes are serialised.** `GBRedisCachingLayer` pushes through one writer coroutine that
  reads the current payload, instead of launching a coroutine per write. Concurrent round trips
  could otherwise complete out of order and leave Redis holding a superseded payload for good,
  which a cold instance would then warm up from. Rapid writes also collapse into a single push.
- **Server-side only, by design.** There is no Android target: Redis has no per-user
  authorization, so shipping credentials in an app and exposing the port would let any
  user read or overwrite everyone's assignments. On mobile, use the on-device cache with
  the default sticky bucket service, or remote evaluation.
- `kotlinx-coroutines-core` is exposed as an `api` dependency, not `implementation`:
  `CoroutineScope` and `CoroutineDispatcher` are required parameters of the public constructors
  and factories, so consumers need them on their compile classpath.
- **Concurrent sticky writes can drop an assignment.** Documents are written whole, having been
  merged against the snapshot the instance last read, so two instances bucketing different
  experiments for the same user at nearly the same time overwrite each other. The lost assignment
  is rebucketed deterministically and normally yields the same variation — unless the experiment
  changed in between. This comes with sharing one store: the TypeScript Redis service writes the
  whole document too. An opt-in atomic read-merge-write is planned for a later release.
- On Redis Cluster, `MGET` across keys in different slots is rejected with `CROSSSLOT`;
  that degrades to a miss. Implement `GBRedisCommands` with a slot-aware fan-out if you
  need cluster support.

---
