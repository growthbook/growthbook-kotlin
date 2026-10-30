![](https://docs.growthbook.io/images/hero-kotlin-sdk.png)

[
![](https://maven-badges.herokuapp.com/maven-central/io.growthbook.sdk/GrowthBook/badge.svg)
](https://mvnrepository.com/artifact/io.growthbook.sdk/GrowthBook)

# GrowthBook - Kotlin SDK

- **Lightweight and fast**
- **Kotlin Multiplatform (Android, iOS, macOS (Apple Silicon), JVM, JS, Wasm)**
    - **Android version 21 & above**
    - **JDK 17 & Above**
    - **iOS (iosX64, iosArm64, iosSimulatorArm64)**

- **Use your existing event tracking (GA, Segment, Mixpanel, custom)**
- **Adjust variation weights and targeting without deploying new code**

See [CHANGELOG.md](CHANGELOG.md) for version history.

## Setup

App level build.gradle (Groovy DSL):

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Add GrowthBook module:
    implementation 'io.growthbook.sdk:GrowthBook:10.0.0'

    // Add Network Dispatcher you prefer:
    // 1) NetworkDispatcherKtor — supports Android, iOS, JVM, JS, Wasm
    implementation 'io.growthbook.sdk:NetworkDispatcherKtor:1.3.0'
    // 2) NetworkDispatcherOkHttp — supports Android and JVM only
    implementation 'io.growthbook.sdk:NetworkDispatcherOkHttp:1.2.0'
}
```

If you are not sure which dispatcher to choose we recommend to use network dispatcher based on Ktor.

The main class of `NetworkDispatcherKtor` artifact is `GBNetworkDispatcherKtor` while the main class of `NetworkDispatcherOkHttp` artifact is `GBNetworkDispatcherOkHttp`.
If you are using other network client for example `android-lite-http` and don't want to have any other network client in your application,
you can provide your own implementation of `NetworkDispatcher` based on your network client.

**Add Internet Permission to your AndroidManifest.xml, if not already added**

  ```kotlin
  <uses-permission android:name="android.permission.INTERNET" />
  ```

## Integration

Integration is super easy:

1. Create a Growth Book API key
2. At the start of your app, do SDK Initialization as per below

Now you can start/stop tests, adjust coverage and variation weights, and apply a winning variation to 100% of traffic,
all within the Growth Book App without deploying code changes to your site.

`initialize()` method should be called in order to obtain SDK instance:

```kotlin
var sdkInstance: GrowthBookSDK = GBSDKBuilder(
    apiKey = <API_KEY>,
    apiHost = <GrowthBook_URL>,
    attributes = < Hashmap >,
    trackingCallback = { gbExperiment, gbExperimentResult -> },
    encryptionKey = <String?>,
    networkDispatcher = <NetworkDispatcher>, // you can use GBNetworkDispatcherKtor() or GBNetworkDispatcherOkHttp()
).initialize()
```
If you are accessing features the first time there will be no features right after `initialize()` method call because features are not got from Backend yet. If you need to access features as soon as possible, you need to use `GBCacheRefreshHandler`. You can pass your implementation of `GBCacheRefreshHandler` through `setRefreshHandler()` method.

> **Threading:** the fetched payload is processed on a background dispatcher, so `GBCacheRefreshHandler` is invoked off the main thread. Marshal back to your UI thread yourself if the callback touches UI state. An exception thrown by your handler is caught and logged, not propagated (so it cannot crash the background scope). `feature()`/`run()` are safe to call from any thread and always evaluate against a single consistent snapshot of the loaded state.

#### There are additional properties which can be setup at the time of initialization

```kotlin
    .setEnabled(true) // Enable / Disable experiments
    .setQAMode(true) // Enable / Disable QA Mode
    .setForcedVariations(<HashMap>) // Pass Forced Variations
    .setInitialFeatures(<GBFeatures>) // Seed bundled fallback features (see below)
    .setInitialPayload(<String>) // Seed a bundled raw API payload (see below)
    .setCacheMaxAge(<Long>) // Cache freshness window in ms (see below)
.initialize()
```

#### Bundled fallback features (`setInitialFeatures`)

For a robust offline-first setup, you can bundle a known-good features payload (snapshotted from the API at build time) and seed the SDK with it at init. This gives flags a valid value from the first millisecond — on first installs, offline launches, and empty/corrupt cache — instead of falling back to hardcoded code defaults.

```kotlin
val bundledFeatures: GBFeatures = mapOf(
    "dark-mode" to GBFeature(defaultValue = GBBoolean(false)),
    "new-checkout" to GBFeature(defaultValue = GBBoolean(true)),
)

var sdkInstance: GrowthBookSDK = GBSDKBuilder(
    apiKey = <API_KEY>,
    apiHost = <GrowthBook_URL>,
    attributes = hashMapOf(),
    trackingCallback = { _, _ -> },
    networkDispatcher = GBNetworkDispatcherKtor(),
)
    .setInitialFeatures(bundledFeatures)
    .initialize()
```

The seeded features are applied immediately. The normal cache/network refresh still runs on top and overwrites the seed as fresher data arrives. Effective precedence: **network > disk cache > seed > code defaults**.

#### Bundled fallback payload (`setInitialPayload`)

`setInitialFeatures` takes an already-decoded feature map, so bundling a snapshot means decrypting it at build time —
shipping plaintext feature definitions inside the app even when payload encryption is on. When the snapshot is encrypted,
or carries more than features — saved groups, contextual bandit definitions, or encrypted variants of any of them —
seed the **raw API payload** instead: the exact JSON body the features endpoint returns, snapshotted into your assets
at build time.

```kotlin
var sdkInstance: GrowthBookSDK = GBSDKBuilder(
    apiKey = <API_KEY>,
    apiHost = <GrowthBook_URL>,
    attributes = hashMapOf(),
    trackingCallback = { _, _ -> },
    encryptionKey = <String?>, // used to decrypt the seed's encrypted* fields too
    networkDispatcher = GBNetworkDispatcherKtor(),
)
    .setInitialPayload(readBundledJsonFromAssets())
    .initialize()
```

This matters for contextual bandits in particular: a bandit rule is inert without its definitions, so a bundled payload
for a bandit-driven feature must go through `setInitialPayload` — otherwise the rule falls back to its marginal weights
until the network responds.

Like `setInitialFeatures`, this is only a seed and the same precedence applies. A payload that cannot be parsed or
decrypted is logged and ignored rather than failing initialization. If both setters are used, the explicit features win
over the payload's.

> **Upgrading from 6.x:** Persistent caching is now implemented on every target — Android, Apple (iOS/macOS) and the JVM (on disk), and JS and wasmJs (browser `localStorage`). The legacy `FeatureCache.txt` → `FeatureCache_<clientKey>.txt` migration applies to Android only, so this upgrade note does not apply to the other targets.

#### Custom cache layer (`setCachingLayer`)

By default the SDK caches feature definitions in the built-in per-platform storage described above. To make GrowthBook persist through **your own** storage instead — a shared KMP key/value store, encrypted storage, or one place to clear/reset all cached state — provide a `GBCachingLayer`:

```kotlin
class MyCachingLayer : GBCachingLayer {
    override fun saveContent(fileName: String, content: String) = myKvStore.put(fileName, content)
    override fun getContent(fileName: String): String? = myKvStore.get(fileName)
}

var sdkInstance: GrowthBookSDK = GBSDKBuilder(
    apiKey = <API_KEY>,
    apiHost = <GrowthBook_URL>,
    attributes = hashMapOf(),
    trackingCallback = { _, _ -> },
    networkDispatcher = GBNetworkDispatcherKtor(),
)
    .setCachingLayer(MyCachingLayer()) // routes both feature and sticky-bucket storage
    .initialize()
```

Values are opaque JSON strings keyed by filename — persist and return them verbatim. When set, the custom layer replaces the built-in cache for both feature definitions and sticky-bucket storage. It may be called in any order relative to the sticky-bucket setters.

#### Cache freshness window (`setCacheMaxAge`)

By default the SDK refetches features from the network on every `initialize()`. Pass `setCacheMaxAge(<ms>)` to define a freshness window: while the cached features are younger than that window, the network call on the next fetch is skipped and the cache is served as the authoritative result. Once the cache is older, the SDK refetches. This is a staleness gate evaluated on the next fetch, not a background polling mechanism.

```kotlin
var sdkInstance: GrowthBookSDK = GBSDKBuilder(
    apiKey = <API_KEY>,
    apiHost = <GrowthBook_URL>,
    attributes = hashMapOf(),
    trackingCallback = { _, _ -> },
    networkDispatcher = GBNetworkDispatcherKtor(),
)
    .setCacheMaxAge(60 * 60 * 1000) // serve cache for up to 1 hour, then refetch
    .initialize()
```

An explicit `refreshCache()` call always bypasses this window and hits the network regardless of how fresh the cache is. The window must be positive — `setCacheMaxAge` throws `IllegalArgumentException` for zero or negative values (as of 7.9.0; previously such a value was accepted and silently disabled the window).

> **Not applicable in Remote Evaluation mode.** With `remoteEval = true` the feature cache is bypassed entirely — payloads are evaluated server-side against the current attributes, while the cache is keyed only by API key, so a cached payload could leak a previous user's evaluation to the next one on the same key. Every fetch goes to the network and nothing is persisted, which makes `setCacheMaxAge`, `setStaleTtl` and `setServeStaleOnError` no-ops in that mode.

#### Stale-while-revalidate (`setStaleTtl` + `setCacheMaxAge`)

`setStaleTtl(<ms>)` turns `setCacheMaxAge` into a full three-tier stale-while-revalidate policy, where `staleTtl` is the inner "fresh" window and `cacheMaxAge` is the outer hard ceiling:

| Cache age | Behaviour |
|---|---|
| `age < staleTtl` | **fresh** — served from cache, network skipped |
| `staleTtl ≤ age < cacheMaxAge` | **stale** — served immediately (never blocks) while a background refresh runs |
| `age ≥ cacheMaxAge` | **expired** — NOT served; treated as a cache miss and refetched from the network |

This lets you decouple *how often you revalidate* from *how old data may get before you refuse to serve it*:

```kotlin
    .setCacheMaxAge(24 * 60 * 60 * 1000) // never serve features older than 24h (hard ceiling)
    .setStaleTtl(60 * 60 * 1000)         // revalidate in the background once older than 1h
```

The hard ceiling (third tier) is armed **only when `staleTtl` is set**. Used alone, `setCacheMaxAge` keeps its original two-tier behaviour: fresh within the window, and beyond it the cache is still served (non-authoritative) while the network revalidates — it is never dropped.

`staleTtl` must be positive (`setStaleTtl` throws `IllegalArgumentException` otherwise) and, when `cacheMaxAge` is also set, smaller than it — that pairing is enforced at construction, so `initialize()` throws if it is violated.

By default the expired (third) tier *fails closed*: past `cacheMaxAge` nothing stale is served, so an offline device falls back to code defaults. Opt into `setServeStaleOnError(true)` for HTTP `stale-if-error` semantics — an expired cache is then served as a last resort **only if** the revalidating network round fails, so an offline client keeps its (stale) flags instead of losing them:

```kotlin
    .setCacheMaxAge(24 * 60 * 60 * 1000)
    .setStaleTtl(60 * 60 * 1000)
    .setServeStaleOnError(true) // offline resilience: serve expired cache only when the network is unreachable
```

While the network is reachable the freshness ceiling still holds (fresh data is never bypassed); the stale fallback applies purely to network failure. Prefer the default (fail-closed) for kill-switch-style flags that must never be stale.

> **Observability caveat:** when the stale fallback is served, it is applied as a non-authoritative payload and your `GBCacheRefreshHandler` is **not** invoked — neither as success nor as failure. The handler's `(Boolean, GBError?)` contract cannot express "stale fallback served", so signalling either side would mislead. Treat `setServeStaleOnError` as a best-effort offline safety net, not something you can observe through the refresh handler.

> **Scope:** the fallback covers *automatic* refreshes (startup, background polling, the stale-while-revalidate round). An explicit `refreshCache()` does not serve an expired cache — it reports the failure through your refresh handler instead. The freshness ceiling itself holds on every path: nothing older than `cacheMaxAge` is ever applied, including on `refreshCache()`.

#### Background polling (`setRefreshInterval`)

For long-lived processes (JVM/backend) you can opt into periodic background refresh as an alternative to SSE. Configure the interval with `setRefreshInterval(<ms>)`, then start/stop the poller via the SDK:

```kotlin
val sdk = GBSDKBuilder(/* … */)
    .setRefreshInterval(5 * 60 * 1000) // poll every 5 minutes
    .initialize()

sdk.startPolling()  // launches the background poller
sdk.stopPolling()   // stops it
```

The poller is a coroutine (not a dedicated thread) on the SDK's background scope, so it is cheap while idle, and it retries failed rounds with capped exponential backoff plus random jitter (so many instances that fail together do not all retry in lockstep). Polling and SSE (`startAutoRefreshFeatures()`) are **mutually exclusive** — starting SSE stops the poller and `startPolling()` is a no-op while SSE is active. Stopping SSE with `stopAutoRefreshFeatures()` releases the slot, so `startPolling()` works again afterwards.

`close()` stops the poller too (along with SSE and the background scope), so disposing the SDK instance is enough — you do not need to call `stopPolling()` first.

> **Mobile note:** the SDK cannot observe app lifecycle, so tie `startPolling()` / `stopPolling()` to your foreground/background transitions to avoid keeping the radio awake in the background. On mobile prefer SSE or the pull-on-access cache window (`setCacheMaxAge` / `setStaleTtl`); background polling is intended mainly for JVM/backend usage.

## Usage

- Initialization returns SDK instance - GrowthBookSDK
  ###### Use sdkInstance to consume below features -


- The feature method takes a single string argument, which is the unique identifier for the feature and returns a
  FeatureResult object.

    ```kotlin
  fun feature(id: String) : GBFeatureResult
    ```
- The featureValue method takes a string argument, which is the unique identifier, and the type of the accessed
feature. Booleans, strings and numbers are returned unwrapped; JSON objects and arrays are returned as `GBJson` and
`GBArray` (`GBArray` also satisfies `List<GBValue>`). It returns `null` if the feature has no value or the value is
not of the requested type.

    ```kotlin
  inline fun <reified V>featureValue(id: String): V?
    ```

  The same function is available as an extension on `IGrowthBookSDK`, with identical behavior, so code written
  against the interface reads values the same way.

- The `decodeAs` extension (in the `GrowthBookKotlinxSerialization` module) decodes a `GBValue` — for example a
  `GBJson` feature value — into your own `@Serializable` model via kotlinx.serialization. It returns `null` if the
  value cannot be decoded into the requested type.

    ```kotlin
  inline fun <reified T> GBValue.decodeAs(json: Json = defaultDecodeJson): T?
    ```

  ```kotlin
  import com.sdk.growthbook.kotlinx.serialization.decodeAs
  import kotlinx.serialization.Serializable

  @Serializable
  data class CheckoutConfig(val title: String, val maxItems: Int)

  // Decode a GBJson feature value into a typed model
  val config: CheckoutConfig? = sdkInstance.featureValue<GBJson>("checkout-config")?.decodeAs<CheckoutConfig>()
  ```

  By default `decodeAs` uses a `Json` that **ignores unknown keys**, so a feature config that gains new fields on the
  backend still decodes into older app models (forward compatibility). Pass your own `Json` to change this — for
  example, to fail on unknown fields instead:

  ```kotlin
  import kotlinx.serialization.json.Json

  val strictJson = Json { ignoreUnknownKeys = false }
  val config = featureValue.decodeAs<CheckoutConfig>(strictJson) // null if the JSON has unmodeled fields
  ```
  
- If you changed, added or removed any features, you can call the refreshCache method to fetch the latest feature
  definitions from the network. It always bypasses the `setCacheMaxAge` freshness window, so it refetches even when the
  cache is still fresh.

  ```kotlin
  fun refreshCache()
  ```

- use setRefreshHandler to set a callback that will be called whenever the cache is refreshed.

  ```kotlin
  fun setRefreshHandler(handler: () -> Unit)
  ```

- method for set prefix of filename in cache directory GrowthBook-KMM.

  ```kotlin
  fun setCacheDirectory(prefix: String = "gbStickyBuckets__"): GBSDKBuilder {}
  ```

- The run method takes an Experiment object and returns an ExperimentResult

  ```kotlin
  fun run(experiment: GBExperiment) : GBExperimentResult
  ```

- Get Context

  ```kotlin
  fun getGBContext() : GBContext
  ```

- Get Features
  ```kotlin
  fun getFeatures() : GBFeatures
  ```

- The setEncryptedFeatures method takes an encrypted string with an encryption key and then decrypts it with the default
  method of decrypting or with a method of decrypting from the user

  ```kotlin
  fun setEncryptedFeatures(encryptedString: String, encryptionKey: String, subtleCrypto: Crypto?){
  ```

- start receiving Features automatically when updated SSE

  ```kotlin
  fun startAutoRefreshFeatures(): Flow<Resource<GBFeatures?>>{}
  ```
- stop receiving Features automatically during SSE connection

  ```kotlin
  fun stopAutoRefreshFeatures() {}
  ```

- set a handler to be notified about only the feature flags that changed on a refresh
  (SSE / network), instead of reacting to the whole feature set — useful to avoid
  invalidating an external cache for unrelated flags. The handler fires after features
  are applied, only on an authoritative result and only when something changed; it does
  not fire for the non-authoritative cached payload served before a network refresh. When
  no features were applied yet, the first call reports the whole set as `added`.

  ```kotlin
  fun setFeaturesChangeHandler(handler: GBFeaturesChangeHandler): GBSDKBuilder
  // GBFeaturesChangeHandler = (GBFeaturesDiff) -> Unit
  // GBFeaturesDiff(added, removed, changed) + hasChanges / changedKeys
  ```

- start / stop background polling (requires `setRefreshInterval(<ms>)` on the builder; mutually exclusive with SSE)

  ```kotlin
  fun startPolling()
  fun stopPolling()
  ```

- The isOn method takes a single string argument, which is the unique identifier for the feature and returns the feature
  state on/off

  ```kotlin
  fun isOn(featureDd: String): Boolean {}
  ```

- The setForcedFeatures method setup the Map of user's (forced) features

  ```kotlin
  fun setForcedFeatures(forcedFeatures: Map<String, GBValue>) {}
  ```

  A forced feature is a local override, not an exposure: its evaluations report no feature usage — no
  `setFeatureUsageCallback`, no plugin `onFeatureEvaluated`, no event logger, and nothing sent by the tracking plugin.
  The same is true in the reference JS SDK.

- The getForcedFeatures method returns the Map of currently set forced features

  ```kotlin
  fun getForcedFeatures(): Map<String, GBValue> {}
  ```

- The setAttributes method replaces the Map of user attributes that are used to assign variations.

  ```kotlin
  fun setAttributes(attributes: Map<String, GBValue>) {}
  ```

- The updateAttributes method shallow-merges into the current attributes instead of replacing them
  (parity with the TypeScript SDK's `updateAttributes`): new keys are added, existing keys are
  overwritten, and untouched keys are preserved. The merge is one level deep — nested `GBJson`/
  `GBArray` values are replaced wholesale. A key mapped to `GBNull` keeps the key with a null value
  (it is **not** removed); to remove a key, rebuild the map with `setAttributes`.

  ```kotlin
  fun updateAttributes(attributes: Map<String, GBValue>) {}
  ```

  Example:
  ```kotlin
  sdk.setAttributes(mapOf("id" to GBString("1")))
  sdk.updateAttributes(mapOf("plan" to GBString("pro")))
  // evaluation now sees both "id" and "plan"
  ```

- The setAttributeOverrides method replaces the Map of attribute overrides used for Sticky Bucketing.

  ```kotlin
  fun setAttributeOverrides(overrides: Map<String, GBValue>) {}
  ```

- If you use Sticky Bucketing and need to guarantee that assignments are loaded before evaluating
  experiments (e.g. after login or user switch), use the coroutine versions:

  ```kotlin
  suspend fun setAttributesSync(attributes: Map<String, GBValue>) {}
  suspend fun updateAttributesSync(attributes: Map<String, GBValue>) {}
  suspend fun setAttributeOverridesSync(overrides: Map<String, GBValue>) {}
  ```

  Example:
  ```kotlin
  lifecycleScope.launch {
      sdk.setAttributesSync(loginAttributes)
      val result = sdk.feature("my-experiment") // sticky buckets guaranteed
  }
  ```

- The setForcedVariations method setup the Map of user's (forced) variations to assign a specific variation (used for
  QA)

  ```kotlin
  fun setForcedVariations(forcedVariations: Map<String, Any>) {}
  ```

## Extensions (GrowthBookExt)

`GrowthBookExt` is a pure-Kotlin companion module with quality-of-life helpers
over the core SDK — no extra runtime dependencies, all Kotlin Multiplatform
targets. It adds typed feature accessors, fallback strategies, a typed `Flag<T>`
API, and DSLs for attributes and SDK configuration.

```groovy
implementation 'io.growthbook.sdk:GrowthBookExt:3.0.1'
```

### Typed feature accessors

Read a feature value with a type and a default instead of unwrapping `GBValue`:

```kotlin
val theme: String  = sdk.getString("theme", default = "light")
val maxItems: Int  = sdk.getInt("max-items", default = 10)
val ratio: Double? = sdk.getDoubleOrNull("ratio")
val payload: GBJson? = sdk.getJson("payload")
```

Each type (`String`/`Boolean`/`Int`/`Long`/`Float`/`Double`) has three variants:

- `getX(id, default)` — value or a constant default
- `getXOrNull(id)` — value or `null`
- `getXOrElse(id) { ... }` — value or a lazily computed default

Boolean helpers: `isEnabled(id)`, `isDisabled(id)`, and `isFeatureKnown(id)`
(distinguishes "missing" from "present but off").

### Fallback strategies

When a feature is *unknown* — i.e. absent from the loaded configuration — choose
fail-open vs fail-closed explicitly at the call site:

```kotlin
if (sdk.isEnabled("new-checkout", FallbackStrategy.FAIL_CLOSED)) { ... }
```

The strategy applies **only** to an unknown feature. A known-but-off feature still
returns its real evaluated value, and so does a loaded feature whose evaluation
fails (malformed rule, failed prerequisite) — an evaluation error is never mistaken
for a missing feature, so `FAIL_OPEN` cannot flip a kill switch on.

> **Startup window.** Feature definitions are fetched asynchronously, so until the
> first payload (or cached payload) is applied *every* feature is unknown, and
> `FAIL_OPEN` reports all of them as enabled — permanently so if the fetch fails and
> no cache exists. Use `suspendFeature`, or seed a bundled payload with
> `initialFeatures`, when a flag must not be read before the SDK is ready.

### Typed flags — `Flag<T>`

Declare flags once (key + type + per-feature default) to remove magic strings:

```kotlin
object Flags {
    val DARK_MODE = Flag("dark-mode", default = false) // Flag<Boolean>
    val MAX_ITEMS = Flag("max-items", default = 10)     // Flag<Int>
}

val dark  = sdk.isOn(Flags.DARK_MODE)  // Boolean
val items = sdk.value(Flags.MAX_ITEMS) // Int, falls back to 10
```

`Flag.default` covers both a missing feature and a present-but-wrong-typed value.
Supported types: `Boolean`/`String`/`Int`/`Long`/`Float`/`Double` (decode custom
`@Serializable` types via the `GrowthBookKotlinxSerialization` module instead).

### Property delegates

Read a flag as a Kotlin property with `by`. The flag is re-evaluated on **every**
read, so the property always reflects the current config — a refreshed payload is
picked up without re-declaring the property:

```kotlin
val newHome by sdk.featureFlag("new-home")                               // Boolean, via isOn
val betaCheckout by sdk.featureFlag("beta-checkout", FallbackStrategy.FAIL_CLOSED)
val maxItems by sdk.featureFlag(Flag("max-items", default = 10))         // Int, falls back to 10

if (newHome) renderNewHome() else renderOldHome()
```

Pure sugar over `isOn` / `isEnabled(id, fallback)` / `value(flag)` — same semantics,
just a delegate form. Handy when a flag is read in several places or grouped as
screen/ViewModel config. In a hot loop, snapshot it into a local `val` to avoid
re-evaluating on each read.

### Attributes DSL

Set targeting attributes with plain Kotlin values, hiding the `GBValue` wrappers:

```kotlin
sdk.setAttributes {
    "id" to "user-123"
    "premium" to true
    "age" to 42
    "tags" to listOf("a", "b")
    "address" to obj {
        "city" to "Kyiv"
    }
}
```

Or build a reusable map: `val attrs = buildAttributes { "id" to "user-123" }`.

Inside the block, `to` on a `String` is the DSL's own entry function and shadows
`kotlin.to`, so nest objects with `obj { }` rather than an inline
`mapOf("city" to "Kyiv")` (a map built outside the block works as a value).

### Configuration DSL

Assemble and initialize the SDK declaratively:

```kotlin
val sdk = growthBook {
    apiKey = "sdk-abc"
    apiHost = "https://cdn.growthbook.io"
    networkDispatcher = GBNetworkDispatcherKtor() // from NetworkDispatcherKtor

    enableLogging = true
    attributes {
        "id" to "user-123"
        "premium" to true
    }
}
```

`apiKey`, `apiHost` and `networkDispatcher` are required (missing →
`IllegalArgumentException`); every other field falls back to the SDK default.

The DSL covers the whole of `GBSDKBuilder`, so nothing forces you back to the
builder: `streamingHost`, `encryptionKey`, `enableLogging`, `remoteEval`, `qaMode`,
`enabled`, `forceVariations`, `trackingCallback`, `refreshHandler`,
`featuresChangeHandler`, `featureUsageCallback`, `initialFeatures`, `plugins`,
`cachingEnabled`, `cacheMaxAge`, `cachingLayer`, and sticky bucketing via either
`stickyBucketService` or `stickyBucketScope` (+ optional `stickyBucketPrefix`).

```kotlin
val sdk = growthBook {
    apiKey = "sdk-abc"
    apiHost = "https://cdn.growthbook.io"
    networkDispatcher = GBNetworkDispatcherKtor()

    plugins = listOf(
        GrowthBookTrackingPlugin.Builder()
            .setClientKey("sdk-abc")
            .setNetworkDispatcher(GBNetworkDispatcherKtor())
            .build()
    )
    cacheMaxAge = 60_000
    stickyBucketScope = viewModelScope
}
```

## Models

This SDK operates with such models as `GBContext`, `GBFeature`, `GBFeatureRule`, `GBFeatureSource`, `GBFeatureResult`, `GBExperiment`, `GBExperimentResult`, etc.

These models can be found in `model` package. Some entities were put in utils/Constants.kt file. In JS SDK there is only one entity "Result" while in this SDK `GBFeatureResult`, `GBExperimentResult` are present.

## Attributes
You can specify attributes about the current user and request. These are used for two things:


1) Feature targeting (e.g. paid users get one value, free users get another)
2) Assigning persistent variations in A/B tests (e.g. user id "123" always gets variation B)
> Attributes can be any JSON data type - boolean, integer, float, string, list, or dict.

## Proguard (Android)

If you're using ProGuard, you may need to add rules to your configuration file to make it compatible with Obfuscation &
Shriniking tools. These rules are guidelines only and some projects require more to work. You can modify those rules and
adapt them to your project, but be aware that we do not support custom rules.

```
# Core SDK
-keep class com.sdk.growthbook.** { *; }

-keep class kotlinx.serialization.json.** { *; }

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.sdk.growthbook.**$$serializer { *; }
-keepclassmembers class com.sdk.growthbook.** {
    *** Companion;
}
-keepclasseswithmembers class com.sdk.growthbook.** {
    kotlinx.serialization.KSerializer serializer(...);
}
```

## Remote Evaluation

This mode brings the security benefits of a backend SDK to the front end by evaluating feature flags exclusively on a
private server. Using Remote Evaluation ensures that any sensitive information within targeting rules or unused feature
variations are never seen by the client. Note that Remote Evaluation should not be used in a backend context.

You must enable Remote Evaluation in your SDK Connection settings. Cloud customers are also required to self-host a
GrowthBook Proxy Server or custom remote evaluation backend.

To use Remote Evaluation, set the `remoteEval = true` property to your SDK instance. A new evaluation API call will be
made any time a user attribute or other dependency changes — specifically on `setAttributes` / `setAttributesSync` /
`updateAttributes` / `updateAttributesSync`, `setAttributeOverrides`, `setForcedFeatures`, and `setForcedVariations`.

> If you would like to implement Sticky Bucketing while using Remote Evaluation, you must configure your remote evaluation
> backend to support Sticky Bucketing. You will not need to provide a StickyBucketService instance to the client side SDK.

## Saved Groups

A saved group is a reusable audience defined once in GrowthBook and targeted from many rules — either an **ID list**
(a set of attribute values) or a **condition group** (a targeting condition). Nothing needs to be enabled in code:
groups arrive in the features payload alongside `features`, plain or encrypted, and the SDK resolves them while
evaluating targeting.

How they reach the SDK depends on the **Saved Groups** setting on the SDK Connection, which is negotiated against the
capabilities of the SDK version registered for that connection — a format the SDK cannot read steps down to the next
one. There are three:

- **Inline** — no `savedGroups` field; the group's contents are copied into every rule that uses it.
- **References (ID lists only)** — rules carry `$inGroup` / `$notInGroup`, and `savedGroups` holds one shared array
  per group. Condition groups are still inlined.
- **References (all types)** — every group travels by reference, through a single `$savedGroup` operator.

The third is the one added in 10.0.0. Its `savedGroups` entries are typed, and rules point at them by id:

```json
{
  "savedGroups": {
    "grp_beta": { "type": "list", "attributeKey": "id", "values": ["u_1", "u_2"] },
    "grp_pro":  { "type": "condition", "condition": { "plan": "pro" } }
  },
  "features": {
    "new-checkout": {
      "defaultValue": false,
      "rules": [
        { "condition": { "$and": [ { "$savedGroup": { "id": "grp_pro" } }, { "country": "US" } ] }, "force": true }
      ]
    }
  }
}
```

Unlike `$inGroup`, `$savedGroup` is not attached to an attribute — it sits alongside `$and` / `$or` / `$not` — so the
entry decides what membership means. A condition group is evaluated in full and may reference further groups; an
optional `attributeKey` on the reference overrides the attribute a list entry names.

Reference resolution is deliberately conservative. A reference the SDK cannot make sense of — an id absent from the
payload, a group type introduced after your SDK version, a malformed entry — matches nobody rather than throwing, so
a payload may safely be newer than the SDK reading it. A group that references itself, directly or around a cycle,
resolves the same way instead of recursing.

Reference formats reduce payload size by shipping each group once instead of per rule. They do **not** keep a group's
members off the client: the contents still travel in the payload. Only [Remote Evaluation](#remote-evaluation) and
remote saved groups do that.

### Remote saved groups

A **remote** saved group keeps its members outside the payload, for lists too large to ship (millions of ids). The
payload only says which attribute the group is about, `{"type": "remote", "attributeKey": "account_id"}`, so the SDK
can't work out membership itself: your app resolves the user's groups, typically from its own back end, and passes
their ids.

```kotlin
val sdk = GBSDKBuilder(/* ... */)
    .setRemoteGroupIds(listOf("grp_vip"))
    .initialize()

// Later, when the user or their groups change
sdk.setRemoteGroupIds(listOf("grp_vip", "grp_beta"))
```

Conditions read the ids as the `__gb_remoteGroupIds` attribute, and remote evaluation sends them too. Setting them
here wins over an attribute of that name, which also works on its own.

Leave the ids unset (or pass `null`) while they are unresolved: rules that use remote groups then fail, including
"not in group" rules, and one warning is logged when logging is enabled. Pass an empty list only when the user is in
no group, because that is what lets "not in group" rules match.

## Contextual Bandits

A contextual bandit splits an experiment's audience into *contexts* (leaves) and gives each leaf its own variation
weights, so traffic is allocated per segment instead of globally. The weight maths (Thompson sampling) runs
server-side — the SDK neither learns nor updates anything. At evaluation time it simply picks the **first leaf whose
condition matches** the user's attributes and buckets by that leaf's weights, using the ordinary experiment machinery.

Nothing needs to be enabled in code. Bandit definitions arrive in the features payload (plain or encrypted, alongside
`features` and `savedGroups`), and a bandit-driven rule is evaluated like any other experiment rule.

What is new is the exposure metadata on `GBExperimentResult`, which lets your warehouse attribute an exposure to the
exact leaf and weight generation that produced it:

```kotlin
val sdkInstance = GBSDKBuilder(
    apiKey = <API_KEY>,
    apiHost = <GrowthBook_URL>,
    attributes = mapOf("id" to GBString("user-123"), "country" to GBString("UA")),
    trackingCallback = { experiment, result ->
        analytics.track(
            event = "experiment_viewed",
            experimentId = experiment.key,
            variationId = result.variationId,
            leafId = result.leafId,                     // which context the user was routed into
            variationWeights = result.variationWeights, // the weights actually used to bucket them
            banditVersion = result.banditVersion,       // which weight generation produced them
        )
    },
    networkDispatcher = GBNetworkDispatcherKtor(),
).initialize()
```

The three fields are populated only for users actually enrolled in a bandit experiment; they are `null` for ordinary
experiments and for users the rule excluded. A `leafId` of `-1` means no leaf condition matched and the rule's aggregate
weights were used instead.

Sticky bucketing works with bandit rules as it does with any experiment rule. One caveat for training pipelines: a
sticky-bucketed user keeps their stored variation, but the exposure reports the *current* leaf's `variationWeights` —
which may differ from the weights in force when they were originally bucketed. Check `result.stickyBucketUsed` before
treating `variationWeights` as the assignment propensities. For offline-first setups, seed the
definitions with [`setInitialPayload`](#bundled-fallback-payload-setinitialpayload) — `setInitialFeatures` does not carry
them.

> **Note:** GrowthBook's querystring-based variation override (`?experiment-key=0`) is not implemented in this SDK, so it
> does not apply to bandit rules either. Use `setForcedVariations` for the same effect.

## Auto-tracking Plugin

`GrowthBookTrackingPlugin` batches experiment exposures and feature evaluations and POSTs them to the GrowthBook ingest
endpoint, so you get tracking without writing a `trackingCallback`. Register it with `setPlugins(...)`:

```kotlin
val sdkInstance = GBSDKBuilder(
    apiKey = <API_KEY>,
    apiHost = <GrowthBook_URL>,
    attributes = mapOf("id" to GBString("user-123")),
    trackingCallback = { _, _ -> },
    networkDispatcher = GBNetworkDispatcherKtor(),
)
    .setPlugins(
        listOf(
            GrowthBookTrackingPlugin.Builder()
                .setClientKey("sdk-abc")
                .setNetworkDispatcher(GBNetworkDispatcherKtor())
                .build()
        )
    )
    .initialize()
```

> **The plugin does not inherit the SDK's network dispatcher.** It is configured separately, which is why the snippet
> above passes one to both. Omit it and the plugin buffers events and discards every batch — it warns about this at
> init, but nothing else signals it. The dispatcher must implement `TrackingNetworkDispatcher`, a separate interface
> from `NetworkDispatcher`: the bundled ones satisfy it from **NetworkDispatcherKtor 1.2.0** and
> **NetworkDispatcherOkHttp 1.1.0** onward, while a custom `NetworkDispatcher` has to implement it explicitly.

Builder options. `setClientKey` and `setNetworkDispatcher` are both **required**: without a key the plugin is a no-op,
and without a dispatcher it buffers events and discards them. Both cases are logged at init:

| Setter | Default | Meaning |
|---|---|---|
| `setClientKey(key)` | **required** | SDK connection key; sent as `?client_key=` |
| `setEnable(bool)` | `true` | master switch; `false` silences the plugin entirely |
| `setIngestorHost(url)` | `https://us-east-1.gb-ingest.com` | ingest base URL; events go to `{host}/track` |
| `setBatchSize(n)` | 100 | events buffered before an eager flush |
| `setBatchTimeout(duration)` | 10 s | how long an event may sit in the buffer |
| `setDedupeCacheSize(n)` | 1000 | LRU window for dropping identical repeated events |
| `setDedupeKeyAttributes(keys)` | `[]` | attributes folded into the de-duplication key |
| `setNetworkDispatcher(d)` | **required** | dispatcher used to POST events |
| `setEnableFeatureUsageEvents(bool)` | `true` | whether `Feature Evaluated` events are sent |
| `setEventFilter(predicate)` | — | drops events the predicate rejects |
| `setCoroutineScope(scope)` | platform tracking dispatcher | where batching and flushing run |

> The default ingest host serves the **us-east-1** Data Region. If your Event Forwarder or Managed Warehouse was created
> in another region, override it — `setIngestorHost("https://eu-west-1.gb-ingest.com")` — otherwise events reach the
> wrong cluster and are dropped.

### Flushing when the app leaves the foreground

Events are sent when the buffer reaches `setBatchSize(n)`, when `setBatchTimeout(duration)` expires, or on `close()`.
That leaves one gap: a process killed while backgrounded takes its buffer with it, so up to a batch timeout's worth of
exposures disappear without a trace. Call `flush()` on the way out to close it — it drains the buffer and leaves the
plugin running, unlike `close()`, which shuts it down for good:

```kotlin
class App : Application(), DefaultLifecycleObserver {
    private val tracking = GrowthBookTrackingPlugin.Builder()
        .setClientKey("sdk-abc")
        .setNetworkDispatcher(GBNetworkDispatcherKtor())
        .build()

    override fun onStop(owner: LifecycleOwner) {                    // backgrounded, not dead
        owner.lifecycleScope.launch { tracking.flush() }
    }

    override fun onTerminate() = tracking.close()
}
```

The equivalent moment is `applicationDidEnterBackground` (or a `scenePhase` change) on Apple targets, and
`visibilitychange` / `pagehide` in the browser — which is where the reference JS plugin hooks itself automatically. The
SDK does not: it takes no dependency on any platform's lifecycle, the same reason `startPolling` / `stopPolling` are
yours to call. Hold the plugin in a field, as above, and pick the moment yourself.

`flush()` is a `suspend` function: it returns once the batch has been handed to the network layer, **not** once it has
been delivered — no dispatcher reports delivery. Waiting for the hand-off is the point; a drain that merely schedules
itself returns before the buffer has even been read, which against a process about to be killed is worth nothing. So it
is worth something on the way into the background and nothing at all as the process is already dying.

It is suspend rather than fire-and-forget so that not waiting is something you say explicitly — `scope.launch {
plugin.flush() }` — and in your own scope rather than the plugin's. `ON_STOP` is not a suspend context, hence the
`lifecycleScope` above; on Apple the function exports as `async`, so a `Task` inside `applicationDidEnterBackground` or
a `scenePhase` observer calls it directly. Safe to call repeatedly and with an empty buffer, and a no-op after
`close()`.

If you registered the plugin through `setPlugins(...)` and kept no reference to it, call **`GrowthBookSDK.flush()`**
instead: it drains every registered plugin implementing `FlushablePlugin` in registration order, with the same
guarantee and the same per-plugin exception isolation as the evaluation callbacks.

```kotlin
override fun onStop(owner: LifecycleOwner) {
    owner.lifecycleScope.launch { sdk.flush() }     // no reference to each plugin needed
}
```

### Turning off feature usage events

A `Feature Evaluated` event fires when a feature's value changes, not on every read (see
[Feature usage reporting](#feature-usage-reporting)) — but a flag that flips regularly still outnumbers
`Experiment Viewed` by a wide margin, and it is usually what consumes an ingest or warehouse quota. Drop them and keep
exposures (including contextual-bandit attribution) with:

```kotlin
GrowthBookTrackingPlugin.Builder()
    .setClientKey("sdk-abc")
    .setNetworkDispatcher(GBNetworkDispatcherKtor())
    .setEnableFeatureUsageEvents(false)
    .build()
```

This affects only what the plugin sends over the network. `GBSDKBuilder.setFeatureUsageCallback` keeps reporting
locally.

### Feature usage reporting

Feature usage is reported on a **value change**, not on every evaluation. Reading a feature in a Compose
recomposition or a render loop produces one report for the first value and nothing after it, until the value actually
moves. This gates all three sinks together — `setFeatureUsageCallback`, every plugin's `onFeatureEvaluated`, and the
event logger (and so the tracking plugin's ingest path) — matching the reference SDK, which de-duplicates the same way.

The history belongs to the user, not to the instance. A user change (`setAttributes` / `updateAttributes` and their
`*Sync` variants) publishes a *new* history alongside the new attributes, in the same atomic swap the rest of the
evaluation state uses; `close()` does the same. So the next user's first read of a feature is always reported even
when the value is the one the previous user saw — and an evaluation that started before the change keeps writing to
the history it started with, so it cannot suppress the new user's first report by finishing late.

Two consequences worth knowing:

- A value returning to an earlier one reports again — the comparison is against the last reported value, not against
  everything ever seen.
- Metadata-only changes do not report: the same value arriving from a different rule or source is not a new usage.
  `GBFeatureResult.source` and `ruleId` are still correct on the value you get back from `feature(id)`; they just do
  not trigger a report on their own.

Forced features report nothing at all, de-duplication aside: a value set through `setForcedFeatures` is a local
override rather than an exposure.

### Filtering events

`setEventFilter` decides per event whether it is sent at all. The predicate receives the event before it is serialized
or de-duplicated, so a rejected event leaves no trace — it does not occupy a slot in the de-duplication cache either:

```kotlin
GrowthBookTrackingPlugin.Builder()
    .setClientKey("sdk-abc")
    .setNetworkDispatcher(GBNetworkDispatcherKtor())
    .setEventFilter { event ->
        event.attributes?.get("internalUser") != GBBoolean(true) &&
            event.eventName != GBTrackingEventNames.FEATURE_EVALUATED
    }
    .build()
```

`event` carries `eventName`, `properties` (exactly what becomes `properties_json`) and `attributes` (what becomes
`context_json` and the promoted identity fields), all as `GBValue`s. Use it for consent gates — unlike `setEnable`, it
is consulted per event, so it can read state that changes at runtime — for keeping attributes off the network, for cost
control, or for sampling.

Two limits worth knowing: the decision is all-or-nothing, so it cannot redact individual fields from an otherwise
acceptable event; and it runs on the evaluation path, so it must be cheap and non-blocking. A filter that throws drops
the event — sending is the only outcome that can leak.

### Custom events

`logEvent` sends your own analytics events through the same pipeline as exposures, so they arrive at the ingest endpoint
batched alongside them:

```kotlin
sdkInstance.logEvent("Checkout Completed", mapOf("plan" to GBString("pro"), "amount" to GBNumber(42)))
```

The instance's current attributes are attached automatically, so the event carries the same identity as an evaluation
made at the same moment. Custom events are not de-duplicated — logging the same event twice means it happened twice.
The one exception is a custom event named after one of the SDK's own (`Feature Evaluated`, `Experiment Viewed`): the
de-duplication rule follows the event name, so such an event is treated as the SDK's, exactly as in the JS plugin.

### Structured event logger

To route events somewhere other than GrowthBook, register a logger instead of (or alongside) the plugin:

```kotlin
GBSDKBuilder(/* … */)
    .setEventLogger { eventName, properties, attributes -> analytics.track(eventName, properties) }
    .initialize()
```

It receives **every** event the SDK produces as one flat stream — `Experiment Viewed` and `Feature Evaluated` from
evaluation, plus explicit `logEvent` calls — under the names in `GBTrackingEventNames` and with the same property keys
as the JS and Java SDKs, so a warehouse pipeline needs no per-SDK mapping:

| Event | Properties |
|---|---|
| `Experiment Viewed` | `experimentId`, `variationId`, `hashAttribute`, `hashValue` (+ `leafId`, `variationWeights`, `banditVersion` for contextual-bandit exposures) |
| `Feature Evaluated` | `feature`, `source`, `value`, `ruleId`, `variationId` |

The logger is additive: it fires *in addition to* `trackingCallback`, `setFeatureUsageCallback` and any registered
plugins, so adding one never costs another sink its events. It is invoked on the evaluating thread — return quickly and
hand slow work to your own executor — and a logger that throws is logged and swallowed rather than surfacing to the
caller or breaking evaluation.

Beyond `GrowthBookPlugin` itself there are three opt-in capability interfaces. Implement the ones your plugin needs
alongside it; plugins that do not implement one are skipped for that hook entirely.

| Interface | Implement it when | What you get |
|---|---|---|
| `CustomEventReceiver` | you want `logEvent(...)` calls | `onEvent(eventName, properties, attributes)` |
| `AttributesChangeReceiver` | your plugin holds per-user state | `onAttributesChanged(attributes)` on a user change |
| `FlushablePlugin` | your plugin buffers work | your `flush()` joins the `GrowthBookSDK.flush()` fan-out |

They are separate interfaces rather than new members on `GrowthBookPlugin` because Kotlin interfaces export to
Objective-C with every member `@required`, so a new member would break existing Swift conformances even with a default
body — a brand-new interface breaks nobody. Every future optional hook follows the same shape. (This is the
[Interface Segregation](https://en.wikipedia.org/wiki/Interface_segregation_principle) split the JDK makes between
`Closeable` and `Flushable`, for the same reason: a plugin should not have to stub a hook it does not want.)

> `logEvent` lives on `GrowthBookSDK`, not on `IGrowthBookSDK`: adding an abstract member would break every
> implementor of that interface. Code written against the interface cannot log custom events, so hold the concrete type
> where you need them. `FakeGrowthBook` mirrors the method (with `loggedEvents()` / `wasLogged(name)` to assert on),
> which is likewise reachable only through the concrete type.

### Reusing one instance across users

One instance commonly serves several users in turn — login, logout, account switching. Two layers of de-duplication
sit between an evaluation and the ingest endpoint, and both are keyed on what was evaluated rather than on who
evaluated it, so both are reset when the user changes:

| Layer | Key | Reset on user change |
|---|---|---|
| SDK (see [Feature usage reporting](#feature-usage-reporting)) | feature key → last reported value | yes — a fresh history is published with the new attributes |
| Tracking plugin | event name + full properties, LRU of `setDedupeCacheSize(n)` | yes — the cache is cleared |

A user change is `setAttributes` / `updateAttributes` or their `*Sync` variants actually changing the attribute map;
re-setting an identical map is not one and leaves both layers alone. `setAttributeOverrides` is not one either — an
override adjusts sticky-bucket identity for the same user.

With both layers resetting, the next user's first evaluation of a feature is always reported, even when the value is
the one the previous user saw — no configuration needed. `setDedupeKeyAttributes` remains available for the cases the
reset does not cover: events still sitting in the plugin's buffer when the user switches, and `Experiment Viewed`
(which already carries `hashAttribute` / `hashValue` in its properties, so it was never at risk). It is also what the
JS plugin exposes, so a configuration written against that SDK keeps working here.

The two layers differ in one respect. The SDK layer is swapped, not cleared, so an evaluation that began before the
user changed reports into the previous user's history and cannot affect the new one. The plugin's cache is a single
LRU with no notion of which user an event came from, so such a late event does take a slot in the cleared cache, and
the new user's identical event is suppressed. `setDedupeKeyAttributes(listOf("id"))` closes that too: the late event
carries the previous user's attributes, so it keys to a different slot. Worth setting if you switch users on a live
instance while evaluations may be in flight.

```kotlin
GrowthBookTrackingPlugin.Builder()
    .setClientKey("sdk-abc")
    .setNetworkDispatcher(GBNetworkDispatcherKtor())
    .setDedupeKeyAttributes(listOf("id"))
    .build()
```

> **Migrating from `TrackingPluginConfig`.** `GrowthBookTrackingPlugin(TrackingPluginConfig(clientKey = "sdk-abc"))`
> still behaves exactly as before, but the config class and the constructor that takes it are deprecated as of 9.0.0 and
> will be removed in a future major release. They are frozen at the options they shipped with, so anything added later
> (starting with `enableFeatureUsageEvents`) is available through the builder only.

## Sticky Bucketing

By default, GrowthBook does not persist assigned experiment variations for a user.
We rely on deterministic hashing to ensure that the same user attributes always map to the same experiment variation.
However, there are cases where this isn't good enough. For example, if you change targeting conditions
in the middle of an experiment, users may stop being shown a variation even if they were previously bucketed into it.
Sticky Bucketing is a solution to these issues. You can provide a Sticky Bucket Service to the GrowthBook instance
to persist previously seen variations and ensure that the user experience remains consistent for your users.

Sticky bucketing ensures that users see the same experiment variant, even when user session, user login status, or
experiment parameters change. See the [Sticky Bucketing docs](https://docs.growthbook.io/app/sticky-bucketing) for more
information. If your organization and experiment supports sticky bucketing, you can implement an instance of
the `StickyBucketService` to use Sticky Bucketing. For simple bucket persistence using the CachingLayer.

Sticky Bucket documents contain three fields:
* attributeName - The name of the attribute used to identify the user (e.g. id, cookie_id, etc.)
* attributeValue - The value of the attribute (e.g. 123)
* assignments - A dictionary of persisted experiment assignments. For example: {"exp1__0":"control"}

The attributeName/attributeValue combo is the primary key.

Here's an example implementation using a theoretical db object:
```kotlin
class GBStickyBucketServiceImp(
    override val coroutineScope: CoroutineScope,
    private val prefix: String = "gbStickyBuckets__",
    private val localStorage: CachingLayer? = null
) : GBStickyBucketService {
    override suspend fun getAssignments(
        attributeName: String,
        attributeValue: String
    ): GBStickyAssignmentsDocument? {
        val key = "$attributeName||$attributeValue"

        localStorage?.let { localStorage ->
            localStorage.getContent("$prefix$key")?.let { data ->
                return try {
                    Json.decodeFromJsonElement<GBStickyAssignmentsDocument>(data)
                } catch (e: Exception) {
                    null
                }
            }
        }

        return null
    }

    override suspend fun saveAssignments(doc: GBStickyAssignmentsDocument) {
        val key = "${doc.attributeName}||${doc.attributeValue}"

        localStorage?.let { localStorage ->
            try {
                val docDataString = Json.encodeToString(doc)
                val jsonElement: JsonElement = Json.parseToJsonElement(docDataString)
                localStorage.saveContent("$prefix$key", jsonElement)
            } catch (e: Exception) {
                // Handle JSON serialization error
            }
        }
    }

    override suspend fun getAllAssignments(attributes: Map<String, String>): Map<String, GBStickyAssignmentsDocument> {
        val docs = mutableMapOf<String, GBStickyAssignmentsDocument>()

        attributes.forEach { (key, value) ->
            getAssignments(key, value)?.let { doc ->
                val docKey = "${doc.attributeName}||${doc.attributeValue}"
                docs[docKey] = doc
            }
        }

        return docs
    }
}
```

## License

This project uses the MIT license. The core GrowthBook app will always remain open and free, although we may add some
commercial enterprise add-ons in the future.
