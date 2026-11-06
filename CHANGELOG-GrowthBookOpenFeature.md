# Changelog — GrowthBookOpenFeature

All notable changes to the `GrowthBookOpenFeature` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

## [1.0.0] - 2026-11-06

### Added
- First release. `GrowthBookOpenFeatureProvider`, an [OpenFeature](https://openfeature.dev) provider for the
  OpenFeature Kotlin SDK 0.9.x on Android and the JVM, backed by a GrowthBook SDK instance the
  provider creates and owns. Supports local evaluation with fetched, cached or supplied payloads.
- OpenFeature context attributes become GrowthBook attributes, and `targetingKey` becomes the `id`
  attribute. `identityPrecedence` decides which wins when the context has both.
- Initialization waits for the first fetch, up to `initializationTimeout` (10 seconds by default).
  Refreshes emit configuration-changed, stale and ready events.
- Strict typed values: no conversion between types, whole-number integers only, and a `null` flag
  value returns the caller's default.
- Experiment exposures are reported through `trackingCallback` after the provider releases its
  internal lock, so a slow callback doesn't block other evaluations.
- Remote evaluation and sticky bucketing are not supported yet; configuring a sticky bucket service
  fails initialization.
