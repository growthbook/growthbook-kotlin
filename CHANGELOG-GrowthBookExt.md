# Changelog — GrowthBookExt

All notable changes to the `GrowthBookExt` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---
## [1.1.0] - Unreleased

### Fixed
- Pinned the JVM toolchain to JDK 17 (`jvmToolchain(17)`) so the published `-jvm`
  artifact always contains Java 17 bytecode (class file 61) regardless of the JDK used
  to build it (#250).

### Compatibility
- **Java 17 is now the explicit minimum runtime floor for the JVM and Android artifacts.**
  Consumers must run on a Java 17-or-newer runtime; older runtimes fail at class load with
  `UnsupportedClassVersionError`.

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
