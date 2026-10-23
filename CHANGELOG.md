# Changelog

All notable changes to the GrowthBook Kotlin SDK will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---
## [Unreleased]

### Fixed
- A prerequisite (`parentConditions`) with a malformed condition, such as `{"$not": null}`, no longer
  matches, so the gated rule or experiment is skipped (shared spec 0.10.1).

### Breaking changes
- **Strict types in condition evaluation** (shared spec 0.10.0, growthbook/growthbook#7379). Conditions no
  longer coerce attribute types. When the attribute's type differs from the condition's, the positive
  operator fails and its negation matches:
  - `"10"` does not equal `10`, `0` does not match null or a missing attribute, and `false` does not match `""`.
  - Two strings compare as text (`"10" < "9"` is true); a string never compares with a number.
  - Booleans are not ordered: `$gt`, `$gte`, `$lt` and `$lte` always fail on them.
  - Version operators (`$veq`, `$vgt`, …) need both sides to be strings, or both numbers. A missing, null,
    boolean, array or float attribute against a version string fails; `$vne` matches.
  - A missing attribute fails comparison operators.
  - `$exists` and `$type` are unchanged.

  Targeting that relied on coercion, such as a numeric attribute sent as a string, stops matching. Send
  attributes with the type the condition uses.

## [9.0.0] - 2026-10-16

A major because two public contracts change with no signature change and no compile error to
announce them: **feature usage is reported on a value change rather than on every evaluation**, and
**forced features report nothing at all**. Code written against 8.x keeps compiling and keeps
running — it simply reports far less often, which a consumer discovers by noticing their numbers
dropped. A silent contract change is worse than a signature break, not better, so it does not ship
in a minor. See *Breaking changes* for what to check.

### Added
- **`GrowthBookTrackingPlugin.Builder`** — a fluent builder that is now the recommended way to configure the built-in
  tracking plugin:
  ```kotlin
  GrowthBookTrackingPlugin.Builder()
      .setClientKey("sdk-abc")
      .setNetworkDispatcher(GBNetworkDispatcherKtor())
      .setEnableFeatureUsageEvents(false)
      .build()
  ```
  Passing a `TrackingPluginConfig` keeps working unchanged. That class has a public constructor, so every option added
  to it would break binary compatibility and leave behind a permanent constructor overload. New options live on the
  builder and in an internal options type instead, so the plugin's configuration can keep growing in minor releases.
  A config built the old way simply takes the defaults for options it cannot express.
- **`setEnableFeatureUsageEvents(false)`** suppresses `Feature Evaluated` events while keeping `Experiment Viewed`
  exposures (and their contextual-bandit attribution) flowing. Feature evaluations outnumber exposures by orders of
  magnitude, so they dominate ingest cost and warehouse quota; suppressed events are dropped before the de-dupe cache
  and the buffer, so they cost nothing. Mirrors the TS SDK's `enableFeatureUsageEvents`
  ([growthbook#6798](https://github.com/growthbook/growthbook/pull/6798)). It does not affect
  `GBSDKBuilder.setFeatureUsageCallback`, which reports evaluations locally and never leaves the process.

- **`setEventFilter { event -> … }`** drops the events a predicate rejects, closing the last gap with the TS plugin's
  option set. The predicate receives a `GBTrackingEventData` — `eventName`, the `properties` that become
  `properties_json` and the `attributes` that become `context_json`, all as `GBValue`s — *before* serialization and
  *before* de-duplication, so a rejected event leaves nothing behind, not even an LRU slot. Intended for consent gates
  (unlike `setEnable` it is consulted per event, so it can read runtime state), for keeping attributes off the network,
  for cost control and for sampling. It cannot redact individual fields — the decision is per event — and a filter that
  throws drops the event, since sending is the only outcome that can leak.
- **`setEnable(false)`** silences the tracking plugin entirely: every event method returns immediately, nothing is
  buffered, de-duplicated or sent, and `close()` still completes cleanly. Lets a debug build, an environment without an
  ingest quota, or an ungranted consent gate keep the plugin registered instead of assembling a different plugin list.
  Mirrors the TS plugin's `enable`. Note it is the plugin's switch, not the SDK's `setEnabled`.
- **`setDedupeKeyAttributes(keys)`** folds the listed attributes into the tracking plugin's de-duplication key, on top of
  the event name and properties. A `Feature Evaluated` event carries no unit identity of its own, so a single SDK
  instance that serves several users over its lifetime (login, logout, account switching via `setAttributes`) would drop
  the next user's identical evaluation as a duplicate of the previous user's. Listing the identifier attribute — usually
  `"id"` — keeps them apart. Empty by default, which preserves the previous behaviour. `Experiment Viewed` was never
  affected, since its properties already carry `hashAttribute`/`hashValue`, but configured attributes apply to both event
  types, matching the TS plugin's `dedupeKeyAttributes`.

- **`GrowthBookTrackingPlugin.flush()`** sends whatever is buffered and leaves the plugin running, which `close()` does
  not. Until now events left the buffer only at `setBatchSize`, on the `setBatchTimeout` timer, or on `close()` — so a
  process killed while backgrounded took up to a batch timeout's worth of exposures with it, silently. Call it from
  `ON_STOP` on Android, `applicationDidEnterBackground` on Apple. The reference JS plugin hooks `visibilitychange` /
  `pagehide` itself; the SDK deliberately takes no dependency on any platform's lifecycle (the same reason
  `startPolling` / `stopPolling` are the consumer's to call), so it exposes the drain and the host app picks the moment.
  Safe to call repeatedly, with an empty buffer, and after `close()` (a no-op). It is a `suspend` function and returns
  once the batch reaches the network layer, not once it is delivered — no dispatcher in the SDK reports delivery.
  Waiting for the hand-off is the point: a fire-and-forget drain returns before the buffer has even been read, which
  against a process about to be killed is worth nothing. Not waiting is therefore something the caller says explicitly
  — `scope.launch { plugin.flush() }` — in their own scope rather than the plugin's. Android's `ON_STOP` is not a
  suspend context, so go through `lifecycleScope`; on Apple the export is an `async` function.
  - **`GrowthBookSDK.flush()`** drains every registered plugin that implements the new **`FlushablePlugin`**, in
    registration order, so a consumer who passed plugins to `setPlugins(...)` does not have to keep a reference to each
    one to drain it. Per-plugin exception isolation as everywhere else; `CancellationException` propagates rather than
    being swallowed, since unlike the other fan-outs this one can be cancelled with its caller. The plugin's own
    `flush()` stays directly callable. Not on `IGrowthBookSDK` — an abstract member breaks implementors, and a fake has
    nothing to drain.
  - `FlushablePlugin` is a third opt-in capability interface rather than a member on `GrowthBookPlugin`, for the reason
    given for `CustomEventReceiver`: Kotlin interfaces export to Objective-C with every member `@required`, so adding
    one would break existing Swift conformances even with a default body. It is also the sibling of
    `GrowthBookPlugin.close()` — same drain, without ending the plugin — which is why the SDK now fans out both.
- **Custom events.** `GrowthBookSDK.logEvent(eventName, properties)` sends your own analytics events through the same
  pipeline as exposures, closing the gap with the reference SDKs' `logEvent` / `log_event`. The instance's current
  attributes are attached automatically, and custom events are not de-duplicated, matching the JS plugin, which
  de-duplicates only `Feature Evaluated` and `Experiment Viewed` — a custom event *named* after one of those follows
  their rule, again as in the JS plugin. The built-in tracking plugin batches them to the ingest
  endpoint in the same envelope as the auto-tracked events.
    - `GBSDKBuilder.setEventLogger { eventName, properties, attributes -> … }` registers one structured sink for
      **every** event the SDK produces — `Experiment Viewed` and `Feature Evaluated` from evaluation plus explicit
      `logEvent` calls — using the event names in `GBTrackingEventNames` and the same property keys as the JS and Java
      SDKs, so a warehouse pipeline needs no per-SDK mapping. It is additive rather than a replacement: `trackingCallback`,
      `setFeatureUsageCallback` and registered plugins all keep firing, unlike the JS SDK's single overwritable slot,
      where the tracking plugin silently displaces a logger the consumer set. It runs on the evaluating thread, and a
      throwing logger is logged and swallowed rather than surfacing to the caller or breaking evaluation.
    - Plugins opt in by implementing the new `CustomEventReceiver` interface alongside `GrowthBookPlugin`; plugins that
      do not are skipped. It is deliberately a separate interface rather than another member on `GrowthBookPlugin`:
      Kotlin interfaces export to Objective-C with every member `@required`, so a new member would break existing Swift
      conformances even with a default body. Future optional hooks should follow the same shape.
    - `FakeGrowthBook` records `logEvent` calls; assert on them with `loggedEvents()` / `wasLogged(name)`. Note that
      `logEvent` is on `GrowthBookSDK` and `FakeGrowthBook` but **not** on `IGrowthBookSDK` — adding an abstract member
      would break every implementor — so code written against the interface cannot log custom events. Hold the concrete
      type where you need them.
- **`AttributesChangeReceiver`** — a second opt-in plugin capability, in the same shape as `CustomEventReceiver`: a
  plugin implementing it is told when the instance starts serving a different user, so it can reset per-user state.
  `onAttributesChanged(attributes)` fires from `setAttributes`, `updateAttributes` and their `*Sync` variants, and only
  when the resulting map actually differs — re-setting an identical map is silent, since attribute setters are called
  routinely on screens where nothing changed. `setAttributeOverrides` does not fire it: overrides adjust sticky-bucket
  identity for the same user.
  - The built-in tracking plugin implements it and **clears its de-duplication cache on a user change**. A
    `Feature Evaluated` event carries no unit identity, so on login/logout the next user's identical evaluation used to
    be suppressed as a duplicate of the previous user's and never reached the warehouse — the failure mode
    `setDedupeKeyAttributes` was added for. That option still matters (it separates users *within* the cache), but the
    default no longer loses an exposure.
  - The plugin's cache can only be cleared, which is weaker than the SDK-level gate above: it holds no record of which
    user an entry came from, so an evaluation finishing after the switch takes a slot in the freshly cleared cache and
    suppresses the next user's identical event. `setDedupeKeyAttributes(listOf("id"))` covers that window, since the
    late event carries the previous user's attributes.

### Changed
- The tracking plugin's default ingest host is now `https://us-east-1.gb-ingest.com`, catching up with
  [growthbook#6608](https://github.com/growthbook/growthbook/pull/6608), which replaced the legacy `us1.gb-ingest.com`
  name across the SDK, the back end, the app and the docs. **Anyone who never set `ingestorHost` explicitly is now
  posting to a different hostname.** Both names address the us-east-1 region, so no configuration change is needed;
  override with `setIngestorHost(...)` if your Data Region is not us-east-1.
  - `TrackingPluginConfig.DEFAULT_INGESTOR_HOST` is a `const val`, so its value is inlined into the bytecode of
    anything that reads it. Code that referenced the constant explicitly keeps the old `us1.gb-ingest.com` string
    until it is recompiled against this release; the SDK's own default resolves at runtime and is unaffected.

### Fixed
- A throwing `trackingCallback` no longer costs plugins and the event logger a remote-eval exposure. On the `rule.tracks`
  path the consumer callback and the plugin/logger fan-out shared one `try`, so a callback that threw skipped the
  fan-out — and because `gbExperimentHelper.isTracked(...)` had already marked the exposure, it was never offered again.
  The two are now separate, matching how `GBExperimentEvaluator`'s step 18 and `prepareResult` already handled it.

### Breaking changes
The first two change no signature at all — they change what an existing call *means*, which is what makes this a major
rather than a minor: code written against 8.x keeps compiling and keeps running, it simply reports far less often, and
nothing announces that. Recompilation is not enough; check both behaviours against what your code assumes. The third is
an ordinary removal, caught by the compiler, and rides along.

- **Feature usage is reported on a value change, not on every evaluation.** A feature read in a recomposition or a
  render loop reports once for its first value and stays quiet until the value actually moves. This gates all three
  sinks together — `setFeatureUsageCallback`, every plugin's `onFeatureEvaluated`, and the event logger with the
  tracking plugin behind it — matching the reference SDK, which de-duplicates in exactly this place and gates the same
  set. **This is a silent behaviour change**: a callback used as a per-evaluation trace now fires far less often.
  Related notes:
  - The history is keyed by feature key and holds the last reported value. A value returning to an earlier one
    reports again; a metadata-only change (same value, different rule or source) does not.
  - It belongs to the user rather than to the instance. A user change (`setAttributes` / `updateAttributes` and their
    `*Sync` variants) publishes a fresh history in the same atomic swap as the new attributes, so an evaluation that
    started beforehand writes into the history it started with and cannot suppress the next user's first report by
    finishing late. `close()` resets it too. The reference SDK keeps one instance-wide map across attribute changes
    and clears it only on destroy — the deviation is deliberate, and removes the case its own tracking plugin needs
    `dedupeKeyAttributes` to work around.
  - Comparison is by `GBValue` equality rather than by serialized form, so a value alternating between `1` and `1.0`
    reports twice where the reference SDK reports once. Serializing on every evaluation to match would allocate on the
    evaluation path for a distinction that cannot change what the value means.
- **Forced features no longer report feature usage.** A feature served from `setForcedFeatures(...)` — i.e. a result
  whose `source` is `GBFeatureSource.override` — no longer fires `setFeatureUsageCallback`, `GrowthBookPlugin
  .onFeatureEvaluated` or the event logger, so it never reaches the tracking plugin's ingest path either. A forced
  value is a dev/QA override rather than an exposure: reporting it attributes to the user a value they were never
  bucketed into. This matches the reference JS SDK, which skips the whole usage fan-out for
  `source === "override"`. Every other source —
  `defaultValue`, `force`, `experiment`, `prerequisite`, `cyclicPrerequisite`, `unknownFeature` — reports as before,
  and the forced value itself is still served unchanged. **This is a silent behaviour change**: if you relied on the
  usage callback to observe forced features (a local debug log, for instance), read `getForcedFeatures()` instead.
- **`TrackingEvent` is removed from the public API** (now `internal`), along with its constructor, its
  `payload: JsonObject` property and the `forExperiment` / `forFeature` factories. Two reasons, and the second is why it
  could not simply be frozen and deprecated the way `TrackingPluginConfig` was: `payload` is a `kotlinx.serialization`
  type, which the SDK does not expose in its public API (this one slipped through in 7.8.0); and the event properties
  are now carried as `Map<String, GBValue>` so the *same* map feeds the event filter, the event logger and the
  serializer — which is what guarantees a filter sees exactly what gets sent. That reshaped both factories into
  `experimentProperties` / `featureProperties`, so the old signatures no longer describe anything the SDK does.
  - **Migration:** the only part of it a caller could put to use was the two event names. Replace
    `TrackingEvent.EVENT_EXPERIMENT_VIEWED` with `GBTrackingEventNames.EXPERIMENT_VIEWED` and
    `TrackingEvent.EVENT_FEATURE_EVALUATED` with `GBTrackingEventNames.FEATURE_EVALUATED`. Nothing public ever accepted
    or returned the class, so there is nothing else to port.

### Deprecated
- `TrackingPluginConfig` and the `GrowthBookTrackingPlugin(config, coroutineScope)` constructor that takes it. Both
  still work and are unchanged at the bytecode level — this is a warning, not a break — but they are frozen and will be
  removed no earlier than 10.0.0. Migrate to the builder:
  ```kotlin
  // before
  GrowthBookTrackingPlugin(TrackingPluginConfig(clientKey = "sdk-abc", batchSize = 50))
  // after
  GrowthBookTrackingPlugin.Builder().setClientKey("sdk-abc").setBatchSize(50).build()
  ```
  The `TrackingPluginConfig.DEFAULT_*` constants are deprecated along with their owner; the values themselves are
  unchanged and the builder applies them to any option left unset.

### Companion artifacts
- `GrowthBookTest` **3.0.0** — `FakeGrowthBook` gained `logEvent(eventName, properties)` plus `loggedEvents()` and
  `wasLogged(name)` so custom events can be asserted on without a network or a plugin.
- `GrowthBookExt` **3.0.0** — `eventLogger` added to the configuration DSL, mirroring the new builder setter.
- `Core`, `GrowthBookKotlinxSerialization`, `NetworkDispatcherKtor` and `NetworkDispatcherOkHttp` are unaffected and
  keep their current versions.

---
## [8.0.0] - 2026-09-08

### Added
- **Contextual bandits.** The SDK now understands contextual bandit rules and their definitions in the features payload
  (`contextualBandits` / `encryptedContextualBandits`), matching the reference TS SDK against shared spec version 0.8.0.
  A bandit rule carries its variations under `contextualVariations`; at evaluation the SDK routes the user into the
  first context (leaf) whose condition matches and buckets by that leaf's weights. All weight maths stays server-side.
    - New exposure metadata on `GBExperimentResult`: `leafId`, `variationWeights`, `banditVersion`, populated only for
      users actually enrolled in the experiment. `leafId == -1` means no leaf matched and the rule's aggregate weights
      were used.
    - Fallbacks mirror the reference SDK: a dangling `contextualBanditRef` keeps the rule's aggregate weights and emits
      no metadata; empty or non-matching contexts fall back to aggregate (or equal) weights with the `-1` sentinel.
    - Malformed definitions degrade instead of failing (the reference TS SDK types these fields as required; handling
      mirrors the Python SDK): a leaf missing `leafId` or `weights` cannot describe the assignment, so a match on it
      takes the `-1` aggregate-weight fallback — and never discards the rest of the payload; a leaf whose `condition`
      is not a JSON object is skipped (fails closed) rather than coerced into a match-everyone catch-all.
    - Reported `variationWeights` are always the weights the bucketer actually used: a leaf vector the bucketer would
      reject (wrong length, non-finite or negative entries, sum outside [0.99, 1.01]) takes the `-1` fallback, and the
      fallback itself reports the rule's weights after the same substitution the bucketer applies. A rule with explicit
      `ranges` buckets by those ranges and emits **no** bandit metadata, since no weight vector describes a
      ranges-governed assignment (matches the Python SDK).
- The built-in tracking plugin's `Experiment Viewed` event now carries `leafId`, `variationWeights` and `banditVersion`
  for enrolled bandit exposures, so auto-tracking users keep bandit attribution without a manual `trackingCallback`.
  The properties are additive and omitted for ordinary experiments, so non-bandit events keep the exact TS plugin shape
  (the TS plugin does not send these fields yet).
    - Sticky bucketing recognises bandit rules — identifier attributes are now derived from `contextualVariations` as
      well as `variations`. Previously sticky bucketing silently did nothing on a bandit-driven feature.
- `GBSDKBuilder.setInitialPayload(json)` seeds the SDK with a bundled **raw API payload** rather than just features:
  saved groups and contextual bandit definitions are seeded too, including their encrypted variants. Bandit rules are
  inert without their definitions, so offline-first setups covering a bandit-driven feature need this over
  `setInitialFeatures`. A payload that cannot be parsed is ignored instead of failing initialization.

### Changed
- `GBUtils.isIncludedInRollout` aligned with the reference SDK: `coverage == 0` now excludes everyone, and an empty or
  missing hash attribute value excludes the user, instead of both being treated as "included".
- A payload that cannot be decrypted no longer throws. `getFeaturesFromEncryptedFeatures`,
  `getSavedGroupFromEncryptedSavedGroup` and `getBanditsFromEncryptedBandits` now return `null` for a malformed blob or
  a rotated key (previously only a JSON-parse failure was handled, while the split/base64/AES steps threw), so one bad
  field no longer discards the rest of the payload — matching the reference SDK's per-field handling. Consequence for
  `GrowthBookSDK.setEncryptedFeatures`: a payload it cannot decrypt is now ignored instead of throwing at the call site.

### Fixed
- Features, saved groups and contextual bandit definitions from a fetched payload are published to the context in a
  single atomic update. Previously each was a separate write, so an evaluation on another thread could observe new
  features paired with the previous generation's bandit definitions (routing by stale weights, or falling back to
  aggregate weights for a rule whose bandit had just arrived).

### Breaking changes
This release narrows which types application code may **construct**. Reading, passing around and pattern-matching them
is unaffected — only creating instances is now the SDK's job.

- Constructors of SDK-owned types are `internal`, and their `copy()` with them (`@ConsistentCopyVisibility`):
  `GBContext`, `GBOptions`, `StackContext`, `GBFeaturesDiff`, `GBFeatureChange`, `FeaturesDataModel`,
  `GBContextualBandit`, `GBBanditContext`, and every `SerializableGB*` wire type. Build a context through
  `GBSDKBuilder`; the other types only ever arrive from the SDK. This is what lets future payload fields be added to
  them without another major release. Note that Kotlin enforces `internal` at compile time; for Java callers it is a
  declaration of intent rather than a hard lock, and such use is unsupported.
- Types application code legitimately constructs — `GBFeature`, `GBFeatureRule`, `GBExperiment`, `GBExperimentResult`,
  `GBFeatureResult` — keep public constructors, but gained fields for contextual bandits. Adding a constructor
  parameter changes the JVM signature, so code compiled against 7.x must be recompiled against 8.0.0.
- `GBContext.plugins` moved from a mutable property into the constructor as a `val`. Set plugins with
  `GBSDKBuilder.setPlugins()`, as before; assigning after construction was already a silent no-op and is now impossible.
- Because `GBContext` can no longer be constructed by application code, the public `GrowthBookSDK(gbContext, ...)`
  constructor is unreachable in practice. Use `GBSDKBuilder`.
- The internal fetch-result callbacks `featuresFetchedSuccessfully` / `savedGroupsFetchedSuccessfully` on
  `GrowthBookSDK` are replaced by a single `payloadFetchedSuccessfully(features, savedGroups, contextualBandits,
  isRemote)`, which is what makes the payload land atomically (see *Fixed*). They were never part of the documented
  API — `FeaturesFlowDelegate` is internal — but they were reachable from the JVM, hence the note.

### Known limitations
- GrowthBook's querystring variation override is not implemented in this SDK, so the corresponding shared spec case
  (`querystring force overrides CB routing`) is skipped rather than ported. Use `setForcedVariations` instead.
- Exposure deduplication keys on hash attribute/value, experiment key and variation id — the same key the reference
  TS SDK uses. It does not include `leafId` or `banditVersion`, so a user re-routed into a different leaf (or a new
  weight generation) with the same variation index does not fire a new tracking call. Matching the reference SDK is
  deliberate; widening the key is an upstream spec question, not an SDK-local fix.
- A sticky-bucketed user keeps their stored variation, but the exposure reports the *current* leaf's
  `variationWeights`, which may differ from the weights in force when they were originally bucketed (matches the
  reference SDK). Training pipelines should join on `stickyBucketUsed` before treating `variationWeights` as the
  assignment propensities.

### Companion artifacts
- `GrowthBookTest` **2.0.0** — no source changes, but it builds `GBExperimentResult` internally, so the 1.0.0 artifact
  is not binary-compatible with this release and must be upgraded alongside it.
- `GrowthBookExt` **2.0.0** — no source changes; the major bump propagates this release through its `api` dependency.
- `Core`, `GrowthBookKotlinxSerialization`, `NetworkDispatcherKtor` and `NetworkDispatcherOkHttp` are unaffected and
  keep their current versions.

---
## [7.9.0] - 2026-09-03

### Added
- Background polling auto-refresh engine. `GBSDKBuilder.setRefreshInterval(<ms>)` configures a
  periodic network revalidation; start/stop it with `GrowthBookSDK.startPolling()` /
  `stopPolling()`. The poller runs as a coroutine on the SDK's background scope (not a dedicated
  thread), retries failed rounds with capped exponential backoff plus random jitter (so many
  instances that fail together do not all retry in lockstep), and is mutually exclusive with SSE —
  starting SSE stops the poller and `startPolling()` is a no-op while SSE is active; the switch
  between the two mechanisms is race-free. A round that throws is treated as a failed round (logged +
  backoff) rather than terminating the loop. Disabled by default; intended mainly for long-lived
  JVM/backend usage (tie it to app lifecycle on mobile).
- `GBSDKBuilder.setStaleTtl(<ms>)` — turns `setCacheMaxAge()` into a full three-tier
  stale-while-revalidate policy: `age < staleTtl` → fresh (served, network skipped);
  `staleTtl ≤ age < cacheMaxAge` → stale (served immediately + background revalidation);
  `age ≥ cacheMaxAge` → expired (not served, refetched as a cache miss). The hard ceiling (third
  tier) is armed only when `staleTtl` is set; `setCacheMaxAge()` used alone keeps its original
  two-tier behaviour (serve-stale beyond the window, never dropped), so existing consumers are
  unaffected. Set `staleTtl < cacheMaxAge`.
- `GBSDKBuilder.setServeStaleOnError(<Boolean>)` — HTTP `stale-if-error` semantics for the expired
  (third) tier: when enabled, a cache older than `cacheMaxAge` is served as a last resort **only if**
  the revalidating network round fails, so an offline client keeps its stale flags instead of falling
  back to code defaults. Default false fails closed (nothing stale served past the ceiling). The
  freshness ceiling still holds whenever the network is reachable, and it holds on *every* path —
  including an explicit `refreshCache()`, which never applies a payload past `cacheMaxAge`. The
  fallback itself covers automatic refreshes only (startup, polling, the stale-while-revalidate
  round); `refreshCache()` reports the network failure through `GBCacheRefreshHandler` instead,
  since it is coalesced with any in-flight round and a per-caller fallback cannot be attributed.
- `BackoffPolicy` — new public class in `:Core` (`io.growthbook.sdk:Core:1.6.0`): pure, stateless
  capped exponential backoff (`delayFor(attempt)` / `shouldRetry(attempt)`), the shared
  implementation behind every retry path in the SDK. Usable directly by consumers writing their own
  `NetworkDispatcher`.

### Changed
- Exponential backoff is now centralised in `BackoffPolicy` and shared by all three retry paths:
  the polling engine, `suspendFeature()`'s retry loop and SSE reconnection (`SSERetryManager` now
  delegates its delay/attempt maths to it and only owns the reconnection counter). No behaviour
  change: `suspendFeature()` still does initial 1s, doubling, 60s cap, 5 attempts, and SSE
  reconnect still does initial 1s, doubling, 30s cap, 10 attempts.
- A `GBCacheRefreshHandler` that throws is now caught and logged instead of propagating. The SDK's
  background payload-processing scope also carries a `CoroutineExceptionHandler`, so an exception
  escaping a fire-and-forget fetch (e.g. from a consumer handler) is logged rather than reaching the
  platform's default uncaught-exception handler — which on Android crashes the app. This matters most
  under polling, where the fetch path runs repeatedly.
- **Potentially breaking:** `GBSDKBuilder.setCacheMaxAge()` now rejects a non-positive window with
  `IllegalArgumentException` instead of accepting it. A zero/negative window silently disabled the
  freshness gate (every cache entry classified stale), which is indistinguishable from never calling
  the setter — and, now that it doubles as the outer ceiling for `setStaleTtl()`, it would also
  arm a ceiling that expires everything. Callers that were passing a computed value must guard it
  or omit the call.
- `GrowthBookSDK.stopAutoRefreshFeatures()` now also releases the auto-refresh mode, not just the SSE
  connection, so `startPolling()` works after SSE has been stopped (previously nothing claimed or
  released the mode, since polling did not exist). `close()` stops polling as well as SSE.

---
## [7.8.1] - 2026-09-02

### Changed
- The FNV-1a hash behind bucketing (`GBUtils.hash`, hash versions 1 and 2) is now computed with
  plain `Int` arithmetic instead of arbitrary-precision `BigInteger`. `Int` multiplication wraps at
  32 bits, which is exactly the modulo 2^32 the algorithm calls for, so the explicit `mod(2^32)`
  step is gone; the accumulator is widened to an unsigned `Long` once at the end. The old code
  allocated an `FNV` instance per hash (twice per hash-v2 call), computed `BigInteger(2).pow(32)` in
  its constructor, and created roughly three `BigInteger` objects per character; the new code
  allocates nothing. **Hash output is bit-identical for every input** — no user is re-bucketed, and
  hashing stays byte-compatible with the reference (TypeScript) SDK for all ASCII and Latin-1
  inputs, as before

### Removed
- `com.ionspin.kotlin:bignum` is no longer a dependency of the `:GrowthBook` module, since the hash
  rewrite above was its only consumer. It was declared `implementation`, so it never appeared on
  consumers' compile classpath and no consumer code can fail to compile. It does disappear from the
  published POM's `runtime` scope: if your build resolves `bignum` at an older version and was
  silently being upgraded to `0.3.9` through us, it will now resolve to your declared version.
  Declare it explicitly if you depend on it

---
## [7.8.0] - 2026-08-25

### Added
- Plugin system: `GrowthBookPlugin` interface for observing experiment and feature evaluations
- Built-in `GrowthBookTrackingPlugin` that batches events and POSTs them to the GrowthBook ingest endpoint
- `GBSDKBuilder.setPlugins()` to register plugins with the SDK
- `IGrowthBookSDK` interface extracted from `GrowthBookSDK` (`isOn`, `feature`, `suspendFeature`, `run`, `setAttributes`, `setAttributesSync`), so app code can depend on the abstraction and swap in a test double
- New `GrowthBookTest` module providing `FakeGrowthBook`, a deterministic in-memory `IGrowthBookSDK` for unit tests (no network or cache). Supports:
    - Feature overrides — `enable`/`disable`/`setValue`
    - Fixtures & scenarios — `setFeatures(Map)`, `copy()` to fork a base fixture per test, and `FakeGrowthBook.fromFeaturesJson(...)` to load a real dashboard export. `fromFeaturesJson` rejects encrypted, non-object and feature-less JSON with an explanatory error instead of an opaque decoding failure, and tells a bare map containing a flag named `features` apart from a features response
    - Honest `GBFeatureResult.source` — `override` for values set from code, `defaultValue` for seeded or loaded ones, `unknownFeature` for keys never configured — so code and hooks that branch on `source` (`setFeatureUsageCallback`, `GrowthBookPlugin`) are exercised against states production produces
    - Deterministic experiments — `setForcedVariation(key, index)`. Apart from skipping hashing, the returned `GBExperimentResult` matches what the real evaluator builds: the *variation* key, variation meta, and the same out-of-range fallback to the baseline with `inExperiment = false`
    - Interaction assertions — `wasQueried(id)`, `queriedFeatures()`


### Fixed
- `List<*>.toJsonElement()` in `:Core` now passes an already-serialized `JsonElement` through untouched instead of re-encoding it via `toString()`, matching `Map<*, *>.toJsonElement()`. This prevents double-encoding of nested list values when building request bodies
- Feature truthiness now matches the reference (TypeScript) SDK, whose `off = !value` is plain JS falsiness over the decoded value. A feature now evaluates as **off** (`on = false`) when its value is JSON `null`, the empty string (`""`), or zero in *any* numeric representation — `0.0`, `0.0f`, `0L`, `-0.0` and `NaN` included. Previously only `null`, `false` and integer `0` were off, because the zero check compared a boxed `Number` against `Int` `0`; a dashboard `defaultValue` of `0.0` was reported as `on` while every other SDK reported `off`. Values that are truthy in JS — the string `"0"`, `Infinity`, and empty arrays/objects — remain `on`
- An unresolvable feature value (`GBValue.Unknown`) is now **off** as well. It was reported as `on` even though a typed read via `featureValue<T>()` returns `null` for it
- `featureValue<V>(id)` no longer depends on the declared type of the receiver. The `GrowthBookSDK` member and the new `IGrowthBookSDK` extension now share one implementation, so switching a field from `GrowthBookSDK` to `IGrowthBookSDK` cannot change what a read returns (a member always shadows an extension in Kotlin, and the two bodies had diverged)
- `featureValue<V>(id)` dropped its hard-coded list of "supported" types, matching the reference (TypeScript) SDK's `getFeatureValue`, which applies no such gate. Requesting a supertype — `featureValue<Any>(id)` and friends — now returns the value instead of `null`. A type mismatch still returns `null`
- `featureValue<V>(id)` can now read array-valued features, returning them as `GBArray` (symmetric with `GBJson`) — or as `List<GBValue>`, which `GBArray` implements. Arrays previously returned `null` in every case, although the reference SDK's value type (`JSONValue`) includes `Array<JSONValue>`
- `:Core` now declares `kotlinx-coroutines-core` and `kotlinx-serialization-json` as `api` rather than `implementation` dependencies. Both types show up in its public API — `NetworkDispatcher.consumeSSEConnection` returns a `Flow`, while `TrackingNetworkDispatcher.consumePOSTRequest` and the public `toJsonElement()` helpers take/return `JsonElement` — but `implementation` publishes them into `runtimeElements` only. A consumer writing their own `NetworkDispatcher` or `TrackingNetworkDispatcher` therefore could not name those types without declaring kotlinx in their own build

### Changed
- **Behavioral change (spec conformance).** Because of the truthiness fix above, `isOn()` and
  `GBFeatureResult.on` now return `false` — where 7.7.0 returned `true` — for features whose
  resolved value is JSON `null`, `""`, a non-integer zero (`0.0`, `0L`, `-0.0`, `NaN`) or
  `GBValue.Unknown`. The flip only ever goes `on → off`, and it brings the Kotlin SDK in line
  with the reference (TypeScript) SDK; no dashboard change is involved.

  What to audit before upgrading:
    - Feature flags whose `defaultValue` (or any rule value) is `null`, `""` or a decimal zero,
      if the app uses `isOn()`/`on` as a "is this configured?" check rather than reading the value
    - Dashboards and metrics fed by `trackingCallback`, `setFeatureUsageCallback` or a
      `GrowthBookPlugin`: `GBFeatureResult.on`/`off` are reported through all three, so
      "share of users with flag on" can shift without any config change

  Targeting is unaffected: prerequisite rules evaluate the feature *value*, not `on`/`off`.
  Note that the shared cross-SDK spec (`cases.json`) only covers integer `0`, `null` and `false`,
  so a green spec run does not exercise these cases — see `GBFeatureTruthinessTests`

---
## [7.7.0] - 2026-08-24

### Changed
- Feature-flag and experiment targeting is significantly faster for large $in / $nin lists. Targeting conditions are now converted to the internal GBValue tree **once at
  feature load** instead of on every feature() / run() evaluation, and membership checks against arrays of 16+ items use a lazily-built HashSet (O(1) lookup) instead of a
  linear scan. On an internal payload with thousand-item $in targeting, isOn() dropped from ~2.2 ms to ~5 µs. Wire JSON, the public condition shape, and evaluation results are
  unchanged; case-insensitive operators (`$ini` / $nini / `$alli`) keep the existing fold-and-scan path

### Added
- `decodeAs<T>()` extension on `GBValue` (in `GrowthBookKotlinxSerialization`) to decode feature values into typed models via kotlinx.serialization. The default `Json` is tolerant of unknown keys, so feature config objects carrying fields the caller's model does not declare yet still decode successfully. Pass a custom `Json` to override (e.g. `Json { ignoreUnknownKeys = false }` for strict decoding).

### Fixed
- `GBArray` now implements value-based `equals`/`hashCode` (converted to a `data class`), so arrays with equal contents compare as equal.
- $in / $nin against a missing attribute no longer perform a membership lookup with a null value

---
## [7.6.0] - 2026-08-14

### Added
- Persistent feature-definition cache is now implemented on **every target** — previously only Android persisted a cache and the rest were no-ops. Apple (iOS/macOS) writes to `<Application Support>/GrowthBook-KMM/` via `NSFileManager`, the JVM to `<user.home>/.growthbook/GrowthBook-KMM/` (fallback `<java.io.tmpdir>`) via `java.io` — both atomic (temp file + rename) and self-healing on corrupt data — and JS and wasmJs to the browser `localStorage` under the `GrowthBook-KMM/` key namespace, self-healing on corrupt data and treating a disabled/unavailable `localStorage` (e.g. private browsing) as a cache miss rather than an initialization failure
- Remote-evaluation payloads are **not** persisted or served from the cache. The feature cache is keyed only by API key, so serving a remotely-evaluated payload could surface one user's evaluated features to the next after a logout/login on the same key; remote-eval therefore always fetches fresh from the network
- The `wasmJs` target is now configured for the browser (`browser()` instead of `nodejs()`) so it can persist through `localStorage`
- New `macosArm64` target for the `GrowthBook`, `Core`, and `GrowthBookKotlinxSerialization` artifacts
- `GBSDKBuilder.setCachingLayer(GBCachingLayer)` — provide your own cache implementation so GrowthBook persists its cached state through your own storage (e.g. a shared KMP key/value store or encrypted storage) instead of the built-in per-platform cache. Replaces both the feature-definition cache and sticky-bucket storage, and may be called in any order relative to the sticky-bucket setters

---
## [7.5.0] - 2026-08-14

### Added
- `GBSDKBuilder.setFeaturesChangeHandler()` — callback notified with a `GBFeaturesDiff` (added / removed / changed flags) on each refresh, so consumers can react to only the flags that changed instead of the whole feature set. Fires on all update paths (SSE / GET / remote-eval) after features are applied, and only when something changed

### Fixed
- SSE auto-refresh now emits decrypted features to the `Flow` for encrypted-feature projects, instead of a "success with empty data" event (the raw `features` field is null for encrypted payloads)
- An empty features payload (e.g. all flags deleted in the admin) is now applied as an empty feature set instead of surfacing a spurious refresh error

---
## [7.4.0] - 2026-08-14

### Added
- `GrowthBookSDK.updateAttributes()` / `updateAttributesSync()` — shallow-merge user
  attributes into the current map (parity with the TS SDK's `updateAttributes`): new
  keys are added, existing keys overwritten, untouched keys preserved. A `GBNull` value
  keeps the key with a null value (it is not removed); use `setAttributes()` to replace
  the whole map.

### Fixed
- Remote evaluation now re-runs when user attributes or forced features change:
  `setAttributes()`, `setAttributesSync()` and `setForcedFeatures()` were not triggering
  a fresh remote evaluation, so remote-eval consumers kept stale results after those
  changes.
- In remote-eval mode, `suspendFeature()`'s internal retry now goes through the
  remote-eval POST instead of a plain GET, so a retry can no longer momentarily surface
  non-personalized (unevaluated) feature definitions.
- Rapid attribute or forced-feature changes in remote-eval mode could apply an
  out-of-order (stale) evaluation when a slower earlier request completed after a newer
  one; responses from superseded remote-eval requests are now discarded. A caller
  awaiting such a superseded request is no longer reported a successful refresh, so
  `suspendFeature()` cannot return the older, stale evaluation — it re-joins the latest
  generation instead.
- `setForcedFeatures()` and `setAttributeOverrides()` values are now published atomically
  alongside the other evaluation inputs, so an evaluation running on another thread always
  observes the latest values as part of a single consistent snapshot.
- A custom `NetworkDispatcher` that throws synchronously while starting a request is now
  reported through the refresh handler as a fetch failure, instead of being swallowed or
  propagating out of `initialize()`/`setAttributes()`.
- Remote-eval POST body is now well-formed. User attributes and forced features were
  serialized via their `GBValue.toString()` (e.g. `"GBNumber(value=8490047)"`) instead of
  the underlying JSON value, so server-side targeting saw garbage; they are now encoded as
  real JSON. Forced features are also sent as an array of `[key, value]` pairs (matching
  the reference SDK) instead of a JSON object, which the GrowthBook proxy rejected with
  `400 Bad Request`.

---
## [7.3.0] - 2026-07-20

### Added
- `GBSDKBuilder.setCacheMaxAge()` — configurable cache freshness window; while the
  cache is younger than the given age, the next fetch is served from cache and the
  network call is skipped, provided the cached payload actually decodes to usable
  features/savedGroups — a fresh but empty/undecodable cache falls through to the
  network instead. `refreshCache()` always bypasses this window.
- `GrowthBookSDK.close()` — releases the instance's resources (stops any active SSE connection and cancels the background coroutine scope that processes fetched payloads). Call it when the SDK instance is no longer needed (e.g. on logout or before replacing it) to avoid leaking coroutines across repeated initializations

### Fixed
- Sticky-bucket race condition: evaluation could observe a torn mix of state (e.g. freshly fetched features together with stale sticky-bucket assignment docs) when a background refresh ran concurrently with `feature()`/`run()`. All cross-thread evaluation inputs (`features`, `attributes`, `forcedVariations`, `stickyBucketAssignmentDocs`, `stickyBucketIdentifierAttributes`, `savedGroups`) are now published together as a single immutable snapshot behind an atomic reference, so every evaluation reads one consistent view
- Sticky-bucket assignments generated during evaluation are now merged back into the context one key at a time (atomically) instead of writing the whole docs map back after `feature()`/`run()`. The previous whole-map write-back could overwrite assignments produced by a concurrent background refresh
- `savedGroups` passed to the `GrowthBookSDK` constructor were written to an unused private field and never reached evaluation; they are now stored on the context
- Encrypted feature payloads now decode before the sticky-bucket refresh, so sticky-bucket identifier attributes derive from the real features instead of an empty set on the first fetch (which could re-bucket users)

### Changed
- `suspendFeature()` now retries failed fetches with exponential backoff (capped)
  instead of an unbounded recursive loop, preventing DNS request flooding when the
  network is unavailable (#236). A hung network round (a dispatcher that connects but
  never responds) now times out after 30s and is treated as a failed attempt, so
  `suspendFeature()` can no longer hang indefinitely.
- Concurrent feature refreshes are now coalesced into a single shared in-flight
  request, so N parallel `suspendFeature()` callers no longer trigger N network
  fetches.
- The fetched payload (sticky-bucket refresh + feature application + `refreshHandler` invocation) is now processed on a defined background dispatcher (platform IO) instead of an arbitrary continuation thread. The `refreshHandler` callback is therefore invoked on a background thread — marshal back to your UI thread yourself if it touches UI state

### Breaking
- `GBContext` is no longer a `data class`. The compiler-generated `copy()`, `equals()`, `hashCode()` and `componentN()` (destructuring) members are no longer available. The primary constructor signature and all property accessors are unchanged, so normal construction and field access are unaffected

---

## [7.2.0] - 2026-06-12

### Added
- `GBSDKBuilder.setInitialFeatures()` — seed the SDK with a bundled fallback payload; features are applied immediately and the normal cache/network refresh still runs on top (network > disk cache > seed > code defaults)

### Fixed
- Cache write failure (disk full, I/O error) no longer discards a successfully fetched features payload; the write is now isolated in its own try/catch and its failure is logged but does not affect the current session
- Android cache write is now crash-safe: `fsync` is called before rename, and a `false` return from `renameTo` now throws `IOException` instead of silently leaving a stale cache file
- Concurrent SDK instances with the same `clientKey` no longer corrupt the shared cache file; `CachingAndroid` is now a singleton so its per-filename lock correctly serializes all writers
- Upgrading from 6.x to 7.x no longer silently discards the cached features on first launch (Android only — other platforms do not persist a disk cache); `FeatureCache.txt` is automatically migrated to `FeatureCache_<clientKey>.txt`. Apps using multiple SDK instances with different `clientKey`s may see one cold start on the first launch after upgrade — features self-correct after the first successful fetch

---

## [7.1.1] - 2026-04-23

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
