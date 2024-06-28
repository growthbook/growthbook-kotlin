![](https://docs.growthbook.io/images/hero-kotlin-sdk.png)

[
![](https://maven-badges.herokuapp.com/maven-central/io.growthbook.sdk/GrowthBook/badge.svg)
](https://mvnrepository.com/artifact/io.growthbook.sdk/GrowthBook)

# GrowthBook - Kotlin SDK

- **Lightweight and fast**
- **Android apps & JVM projects**
    - **Android version 21 & above**
    - **JDK 17 & Above**

- **Use your existing event tracking (GA, Segment, Mixpanel, custom)**
- **Adjust variation weights and targeting without deploying new code**

## Setup

App level build.gradle (Groovy DSL):

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Add GrowthBook module:
    implementation 'io.growthbook.sdk:GrowthBook:<version>' // 1.1.60 latest version when this file was edited

    // Add Network Dispatcher you prefer:
    // 1) NetworkDispatcherKtor artifact contains the Network Dispatcher based on Ktor artifact
    implementation 'io.growthbook.sdk:NetworkDispatcherKtor:1.0.2'
    // 2) NetworkDispatcherOkHttp artifact contains the Network Dispatcher based on OkHttp artifact
    implementation 'io.growthbook.sdk:NetworkDispatcherOkHttp:1.0.0'
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
    hostURL = <GrowthBook_URL>,
    attributes = < Hashmap >,
    trackingCallback = { gbExperiment, gbExperimentResult -> },
    encryptionKey = <String?>,
    networkDispatcher = <NetworkDispatcher>, // you can use GBNetworkDispatcherKtor() or GBNetworkDispatcherOkHttp()
).initialize()
```
If you are accessing features the first time there will be no features right after `initialize()` method call because features are not got from Backend yet. If you need to access features as soon as possible, you need to use `GBCacheRefreshHandler`. You can pass your implementation of `GBCacheRefreshHandler` through `setRefreshHandler()` method.

#### There are additional properties which can be setup at the time of initialization

```kotlin
    .setEnabled(true) // Enable / Disable experiments
    .setQAMode(true) // Enable / Disable QA Mode
    .setForcedVariations(<HashMap>) // Pass Forced Variations
.initialize()
```

## Usage

- Initialization returns SDK instance - GrowthBookSDK
  ###### Use sdkInstance to consume below features -


- The feature method takes a single string argument, which is the unique identifier for the feature and returns a
  FeatureResult object.

    ```kotlin
  fun feature(id: String) : GBFeatureResult
    ```

- If you changed, added or removed any features, you can call the refreshCache method to clear the cache and download
  the latest feature definitions.

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

- receive Features automatically when updated SSE

  ```kotlin
  fun autoRefreshFeatures(): Flow<Resource<GBFeatures?>>{}
  ```

- Delegate that set to Context successfully fetched features

  ```kotlin
  fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {}
  ```

- Delegate which inform that fetching features failed

  ```kotlin
  fun featuresFetchFailed(error: GBError, isRemote: Boolean) {}
  ```

- The isOn method takes a single string argument, which is the unique identifier for the feature and returns the feature
  state on/off

  ```kotlin
  fun isOn(featureDd: String): Boolean {}
  ```

- The setForcedFeatures method setup the Map of user's (forced) features

  ```kotlin
  fun setForcedFeatures(forcedFeatures: Map<String, Any>) {}
  ```

- The getForcedFeatures method for mapping model object for request's body type

  ```kotlin
  fun getForcedFeatures(): List<List<Any>> {}
  ```

- The setAttributes method replaces the Map of user attributes that are used to assign variations

  ```kotlin
  fun setAttributes(attributes: Map<String, Any>) {}
  ```

- The setAttributeOverrides method replaces the Map of user overrides attribute that are used for Sticky Bucketing

  ```kotlin
  fun setAttributeOverrides(overrides: Map<String, Any>) {}
  ```

- The setForcedVariations method setup the Map of user's (forced) variations to assign a specific variation (used for
  QA)

  ```kotlin
  fun setForcedVariations(forcedVariations: Map<String, Any>) {}
  ```

- Delegate that call refresh Sticky Bucket Service after success fetched features

  ```kotlin
  fun featuresAPIModelSuccessfully(model: FeaturesDataModel) {}
  ```

## Models

```kotlin
/**
 * Defines the GrowthBook context.
 */
class GBContext(
    /**
     * Registered API Key for GrowthBook SDK
     */
    val apiKey: String?,
    /**
     * Host URL for GrowthBook
     */
    val hostURL: String?,
    /**
     * Switch to globally disable all experiments. Default true.
     */
    val enabled: Boolean,
    /**
     * Encryption key for encrypted feature
     */
    val encryptionKey: String?,
    /**
     * Map of user attributes that are used to assign variations
     */
    internal var attributes: Map<String, Any>,
    /**
     * Force specific experiments to always assign a specific variation (used for QA)
     */
    var forcedVariations: Map<String, Any>,
    /**
     * Map of Sticky Bucket documents
     */
    var stickyBucketAssignmentDocs: Map<GBStickyAttributeKey, GBStickyAssignmentsDocument>? = null,
    /**
     * List of user's attributes keys
     */
    var stickyBucketIdentifierAttributes: List<String>? = null,
    /**
     * Service that provide functionality of Sticky Bucketing
     */
    val stickyBucketService: GBStickyBucketService? = null,
    /**
     * If true, random assignment is disabled and only explicitly forced variations are used.
     */
    val qaMode: Boolean,
    /**
     * A function that takes experiment and result as arguments.
     */
    val trackingCallback: (GBExperiment, GBExperimentResult) -> Unit,
    /**
     * Flag for to use Remote Evaluation
     */
    val remoteEval: Boolean = false
)
```

```kotlin
/**
 * A Feature object consists of possible values plus rules for how to assign values to users.
 */
@Serializable
class GBFeature(
    /**
     * The default value (should use null if not specified)
     */
    val defaultValue: JsonElement? = null,
    /**
     * Array of Rule objects that determine when and how the defaultValue gets overridden
     */
    val rules: List<GBFeatureRule>? = null
)

/**
 * Rule object consists of various definitions to apply to calculate feature value
 */
@Serializable
class GBFeatureRule(
    /**
     * Optional targeting condition
     */
    val condition: GBCondition? = null,
    /**
     * Each item defines a prerequisite where a `condition` must evaluate against
     * a parent feature's value (identified by `id`). If `gate` is true, then this is a blocking
     * feature-level prerequisite; otherwise it applies to the current rule only.
     */
    val parentConditions: ArrayList<GBParentConditionInterface>? = null,
    /**
     * What percent of users should be included in the experiment (between 0 and 1, inclusive)
     */
    val coverage: Float? = null,
    /**
     * Immediately force a specific value (ignore every other option besides condition and coverage)
     */
    val force: JsonElement? = null,
    /**
     * Run an experiment (A/B test) and randomly choose between these variations
     */
    val variations: List<JsonElement>? = null,
    /**
     * The globally unique tracking key for the experiment (default to the feature key)
     */
    val key: String? = null,
    /**
     * How to weight traffic between variations. Must add to 1.
     */
    val weights: List<Float>? = null,
    /**
     * A tuple that contains the namespace identifier, plus a range of coverage for the experiment.
     */
    val namespace: JsonArray? = null,
    /**
     * What user attribute should be used to assign variations (defaults to id)
     */
    val hashAttribute: String? = null,

    // new properties v0.4.0
    /**
     * The hash version to use (default to 1)
     */
    val hashVersion: Int? = null,
    /**
     * A more precise version of coverage
     */
    @Serializable(with = RangeSerializer.GBBucketRangeSerializer::class)
    val range: GBBucketRange? = null,
    /**
     * Ranges for experiment variations
     */
    @Serializable(with = RangeSerializer.GBBucketRangeListSerializer::class)
    val ranges: List<GBBucketRange>? = null,
    /**
     * Meta info about the experiment variations
     */
    val meta: ArrayList<GBVariationMeta>? = null,
    /**
     * Array of filters to apply to the rule
     */
    val filters: ArrayList<GBFilter>? = null,
    /**
     * Seed to use for hashing
     */
    val seed: String? = null,
    /**
     * Human-readable name for the experiment
     */
    val name: String? = null,
    /**
     * The phase id of the experiment
     */
    val phase: String? = null,
    /**
     * When using sticky bucketing, can be used as a fallback to assign variations
     */
    val fallbackAttribute: String? = null,
    /**
     * If true, sticky bucketing will be disabled for this experiment.
     * (Note: sticky bucketing is only available
     * if a StickyBucketingService is provided in the Context)
     */
    val disableStickyBucketing: Boolean? = null,
    /**
     * An sticky bucket version number that can be used to force a re-bucketing of users (default to 0)
     */
    val bucketVersion: Int? = null,
    /**
     * Any users with a sticky bucket version less than this will be excluded from the experiment
     */
    val minBucketVersion: Int? = null,
    /**
     * Array of tracking calls to fire
     */
    val tracks: ArrayList<GBTrackData>? = null
)

/**
 * Enum For defining feature value source
 */
enum class GBFeatureSource {
    /**
     * Queried Feature doesn't exist in GrowthBook
     */
    unknownFeature,

    /**
     * Default Value for the Feature is being processed
     */
    defaultValue,

    /**
     * Forced Value for the Feature is being processed
     */
    force,

    /**
     * Experiment Value for the Feature is being processed
     */
    experiment,

    /**
     * CyclicPrerequisite Value for the Feature is being processed
     */
    cyclicPrerequisite,

    /**
     * Prerequisite Value for the Feature is being processed
     */
    prerequisite
}

/**
 * Result for Feature
 */
class GBFeatureResult(
    /**
     * The assigned value of the feature
     */
    val value: Any?,
    /**
     * The assigned value cast to a boolean
     */
    val on: Boolean = false,
    /**
     * The assigned value cast to a boolean and then negated
     */
    val off: Boolean = true,
    /**
     * One of "unknownFeature", "defaultValue", "force", or "experiment"
     */
    val source: GBFeatureSource,
    /**
     * When source is "experiment", this will be the Experiment object used
     */
    val experiment: GBExperiment? = null,
    /**
     * When source is "experiment", this will be an ExperimentResult object
     */
    val experimentResult: GBExperimentResult? = null
)
```

```kotlin
/*
    Defines a single experiment
 */
@Serializable
class GBExperiment(
    /**
     * The globally unique tracking key for the experiment
     */
    val key: String,
    /**
     * The different variations to choose between
     */
    val variations: List<JsonElement> = ArrayList(),
    /**
     * A tuple that contains the namespace identifier, plus a range of coverage for the experiment
     */
    val namespace: JsonArray? = null,
    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    val hashAttribute: String? = null,
    /**
     * How to weight traffic between variations. Must add to 1.
     */
    var weights: List<Float>? = null,
    /**
     * If set to false, always return the control (first variation)
     */
    var active: Boolean = true,
    /**
     * What percent of users should be included in the experiment (between 0 and 1, inclusive)
     */
    var coverage: Float? = null,
    /**
     * Optional targeting condition
     */
    var condition: GBCondition? = null,
    /**
     * Each item defines a prerequisite where a `condition` must evaluate against
     * a parent feature's value (identified by `id`). If `gate` is true, then this is a blocking
     * feature-level prerequisite; otherwise it applies to the current rule only.
     */
    val parentConditions: ArrayList<GBParentConditionInterface>? = null,
    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    var force: Int? = null,
    //new properties v0.4.0
    /**
     * The hash version to use (default to 1)
     */
    var hashVersion: Int? = null,
    /**
     * Array of ranges, one per variation
     */
    @Serializable(with = RangeSerializer.GBBucketRangeListSerializer::class)
    var ranges: List<GBBucketRange>? = null,
    /**
     * Meta info about the variations
     */
    var meta: ArrayList<GBVariationMeta>? = null,
    /**
     * Array of filters to apply
     */
    var filters: ArrayList<GBFilter>? = null,
    /**
     * The hash seed to use
     */
    var seed: String? = null,
    /**
     * Human-readable name for the experiment
     */
    var name: String? = null,
    /**
     * Id of the current experiment phase
     */
    var phase: String? = null,
    /**
     * When using sticky bucketing, can be used as a fallback to assign variations
     */
    val fallBackAttribute: String? = null,
    /**
     * If true, sticky bucketing will be disabled for this experiment.
     * (Note: sticky bucketing is only available
     * if a StickyBucketingService is provided in the Context)
     */
    val disableStickyBucketing: Boolean? = null,
    /**
     * An sticky bucket version number that can be used to force a re-bucketing of users (default to 0)
     */
    val bucketVersion: Int? = null,
    /**
     * Any users with a sticky bucket version less than this will be excluded from the experiment
     */
    val minBucketVersion: Int? = null
)

/**
 * The result of running an Experiment given a specific Context
 */
class GBExperimentResult(
    /**
     * Whether or not the user is part of the experiment
     */
    val inExperiment: Boolean = false,
    /**
     * The array index of the assigned variation
     */
    val variationId: Int = 0,
    /**
     * The array value of the assigned variation
     */
    val value: JsonElement = JsonObject(HashMap()),
    /**
     * The user attribute used to assign a variation
     */
    val hashAttribute: String? = null,
    /**
     * The value of that attribute
     */
    val hashValue: String? = null,


    //new properties v0.4.0
    /**
     * The unique key for the assigned variation
     */
    val key: String = "",
    /**
     * The human-readable name of the assigned variation
     */
    var name: String? = null,
    /**
     * The hash value used to assign a variation (float from 0 to 1)
     */
    var bucket: Float? = null,
    /**
     * Used for holdout groups
     */
    var passthrough: Boolean? = null,
    /**
     * If a hash was used to assign a variation
     */
    val hashUsed: Boolean? = null,
    /**
     * The id of the feature (if any) that the experiment came from
     */
    val featureId: String? = null,
    /**
     * If sticky bucketing was used to assign a variation
     */
    val stickyBucketUsed: Boolean? = null
)
```
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
made any time a user attribute or other dependency changes.

> If you would like to implement Sticky Bucketing while using Remote Evaluation, you must configure your remote evaluation
> backend to support Sticky Bucketing. You will not need to provide a StickyBucketService instance to the client side SDK.

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
    private val prefix: String = "gbStickyBuckets__",
    private val localStorage: CachingLayer? = null
) : GBStickyBucketService {
    override fun getAssignments(
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

    override fun saveAssignments(doc: GBStickyAssignmentsDocument) {
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

    override fun getAllAssignments(attributes: Map<String, String>): Map<String, GBStickyAssignmentsDocument> {
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
 
