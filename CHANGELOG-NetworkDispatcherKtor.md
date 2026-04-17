# Changelog — NetworkDispatcherKtor

All notable changes to the `NetworkDispatcherKtor` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.13] - 2026-04-*

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
