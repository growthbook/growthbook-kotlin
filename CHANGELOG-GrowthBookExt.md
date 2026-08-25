# Changelog — GrowthBookExt

All notable changes to the `GrowthBookExt` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
  an explicit fail-open/fail-closed policy that applies only to an unknown feature.
- Typed flags: `Flag<T>` (key + type + per-feature default) with `value(flag)` and
  `isOn(flag)`. Supported types `Boolean`/`String`/`Int`/`Long`/`Float`/`Double`;
  numeric flags are robust to how the number was stored.
- Property delegates: `featureFlag(key)`, `featureFlag(key, fallback)` and
  `featureFlag(flag)` — read a flag as a Kotlin property with `by`. The flag is
  re-evaluated on every read, so the property always reflects the current config.
- Attributes DSL: `setAttributes { }` / `buildAttributes { }` with an `obj { }` block
  for nested objects, hiding the `GBValue` wrappers.
- Configuration DSL: `growthBook { }` — assemble and initialize the SDK declaratively.

---
