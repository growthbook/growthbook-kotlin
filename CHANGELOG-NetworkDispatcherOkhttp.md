# Changelog — NetworkDispatcherOkHttp

All notable changes to the `NetworkDispatcherOkHttp` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.3.0] - Unreleased

### Added
- Honour the SDK's custom request headers (`apiHostRequestHeaders` /
  `streamingHostRequestHeaders`): implemented the `headers`-carrying overloads of
  `consumeGETRequest`, `consumeGETRequestWithNotModified`, `consumeSSEConnection` and
  `consumePOSTRequest` added in `:Core` 1.7.0. The SSE request is built once and reused, so the
  headers are re-sent on every reconnection attempt. Requires `io.growthbook.sdk:Core:1.7.0`.

### Changed
- The SDK-managed `Cache-Control`, `Content-Type` and `Accept` headers are now set with `header()`
  instead of `addHeader()`, so an SDK value replaces a consumer-supplied one rather than adding a
  second, conflicting header value. Reserved names are also stripped from the consumer's map before
  it is applied. Behaviour is unchanged when no custom headers are configured.

### Fixed
- A request that cannot be assembled — a URL without a scheme, for example — is now reported through
  `onError` instead of escaping the dispatcher's `CoroutineScope`. Previously
  `consumeGETRequest`/`consumeGETRequestWithNotModified` threw before the call was enqueued, so no
  callback ever fired and the uncaught exception crashed the app on Android. `consumePOSTRequest`
  already guarded this.
- The SSE request is now built inside the returned `Flow`, so the same failure is emitted as
  `Resource.Error` rather than thrown synchronously out of `consumeSSEConnection` — which used to
  propagate all the way out of the SDK's public `autoRefreshFeatures()`.

---

## [1.2.0] - 2026-09-09

### Added
- `fetchTimeoutMillis` constructor parameter (default 30 000 ms): a whole-call ceiling
  (`callTimeout`) and matching `readTimeout` for feature GET and POST requests. Previously the
  bare `OkHttpClient()` default applied a silent 10 s per-read timeout to the fetch — too
  tight for a large payload on a slow network, and invisible to the caller. Applied via a
  derived client that shares the injected client's pool and dispatcher; SSE keeps its own
  streaming-tuned connection. Pass `null` to opt out and keep the injected client's own
  configuration

---
## [1.1.1] - 2026-09-03

### Fixed
- `OkHttpLruETagCache.get()` is now serialized under the write lock instead of the read
  lock. The backing `LinkedHashMap` is access-ordered (LRU), so a `get` structurally
  mutates it (re-links the entry to the tail); under the shared read lock, concurrent
  `get`s could corrupt that linked list and desync the size/eviction bookkeeping (rarely
  observable as the entry count exceeding the configured maximum). Reads are now exclusive,
  so the cache is genuinely thread-safe under concurrent access.

---
## [1.1.0] - 2026-08-25

### Added
- Implement `TrackingNetworkDispatcher` to support `GrowthBookTrackingPlugin`

### Changed
- Removed the duplicated internal `toJsonElement` helper and now use the shared implementation from `:Core`

### Fixed
- Both `consumePOSTRequest` overloads now build the request inside a `try`/`catch` and route failures to `onError`. `Request.Builder.url()` raises `IllegalArgumentException` for a URL without a scheme (an `ingestorHost` of `us1.gb-ingest.com`, say); raised inside a bare `launch` it escaped to the platform's uncaught-exception handler, which on Android crashes the app

---

## [1.0.9] - 2026-08-14

### Fixed
- `Map`/`List.toJsonElement()` now pass an already-serialized `JsonElement` through
  untouched (branch added before `Map`/`List`, since `JsonObject`/`JsonArray` are
  themselves `Map`/`List`), so pre-encoded POST-body values are no longer re-stringified
  and double-quoted.

---

## [1.0.8] - 2026-04-23

### Add
- `consumeGETRequestWithNotModified()`

---

## [1.0.7] - 2026-03-27

### Fixed
- ETag cache: log HTTP 304 Not Modified response instead of treating it as error
- fix duplicate class for LRUEtagCache class

---

## [1.0.6] - 2026-02-25

### Removed
- Accept-Encoding from GET request

---

## [1.0.4] - 2025-12-04

### Changed
- fix publishing

---

## [1.0.3] - 2025-11-22

### Changed
- fix publishing

---

## [1.0.2] - 2024-11-18

### Fixed
- AbstractMethodError fix
- Issue #142 fix
### Changed
- Signing signatory unified across artifacts
- Enable compilation targeting JRE 1.8
- Revert AGP to 7.4.2 and JDK to 11
- Dokka plugin version update
### Added
- Kotlin/JS targets support
- iOS support for NetworkDispatcherKtor module

---

## [1.0.1] - 2024-08-06

### Changed
- Bump OkHttp version to 4.9.2
- Dokka plugin version was updated
- flow{} was replaced with callbackFlow{}

---

## [1.0.0] - 2024-06-14

### Added
- initial release

---
