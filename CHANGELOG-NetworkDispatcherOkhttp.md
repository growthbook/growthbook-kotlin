# Changelog — NetworkDispatcherKtor

All notable changes to the `NetworkDispatcherOkhttp` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.8] - 2026-04-*

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
