# Changelog — NetworkDispatcherKtor

All notable changes to the `NetworkDispatcherKtor` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---
## [1.2.0] - Unreleased

### Fixed
- Pinned the JVM toolchain to JDK 17 (`jvmToolchain(17)`) so the published `-jvm`
  artifact always contains Java 17 bytecode (class file 61) regardless of the JDK used
  to build it (#250).

### Compatibility
- **Java 17 is now the explicit minimum runtime floor for the JVM and Android artifacts.**
  Consumers must run on a Java 17-or-newer runtime; older runtimes fail at class load with
  `UnsupportedClassVersionError`.

---
## [1.1.0] - 2026-08-14

### Added
- New `macosArm64` target, so the Ktor dispatcher covers the same Apple platforms as the core SDK (the shared Apple source set now uses `ktor-client-darwin` for iOS and macOS)

### Fixed
- `handleGetRequest`: catch `Throwable` (not just `Exception`) so a Ktor fetch failure on Kotlin/JS and Kotlin/Wasm — which surfaces as a `Throwable` that is not a `kotlin.Exception` (e.g. "Failed to fetch") — is routed to `onError` instead of escaping as an uncaught coroutine error

---
## [1.0.15] - 2026-08-14

### Fixed
- `consumePOSTRequest()` no longer wraps the request in `client.use { }`, which closed the
  shared, long-lived `HttpClient` after the first POST and broke every subsequent
  GET/POST/SSE request. This surfaced in remote-eval mode, where each attribute change
  issues a POST.
- `Map`/`List.toJsonElement()` now pass an already-serialized `JsonElement` through
  untouched (branch added before `Map`/`List`, since `JsonObject`/`JsonArray` are
  themselves `Map`/`List`), so pre-encoded POST-body values are no longer re-stringified
  and double-quoted.

---

## [1.0.14] - 2026-04-30

### Added
- Added `ContentEncoding` plugin to `GBNetworkDispatcherKtor` to enable automatic gzip/deflate response decompression

---

## [1.0.13] - 2026-04-23

### Add
- `consumeGETRequestWithNotModified()`

---

## [1.0.12] - 2026-04-07

### Added
- iOS targets support: `iosX64`, `iosArm64`, `iosSimulatorArm64`
- Shared `iosMain` source set with `ktor-client-darwin`

---

## [1.0.11] - 2026-03-27

### Fixed
- ETag cache: log HTTP 304 Not Modified response instead of treating it as error
- fix duplicate class for LRUEtagCache class

---

## [1.0.10] - 2026-02-25

### Removed
- Accept-Encoding from GET request

---

## [1.0.9] - 2025-12-25
### Added
- Accept-Encoding to GET request
- LRU caching for NotModified response

---

## [1.0.8] - 2025-12-04

### Changed
- fix publishing

---

## [1.0.7] - 2025-11-18

### Changed
- fix publishing

---

## [1.0.6] - 2025-05-13

### Changed
- deprecated method was removed

---

## [1.0.5] - 2025-04-18
### Added
- Kotlin/Wasm initial support
### Changed
- Ktor upgraded to 3.0.3
- Rebase on top of upstream update-dependencies branch

---

## [1.0.4] - 2024-11-22

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

## [1.0.1] - 2024-11-26

### Changed
- Initial release

---
