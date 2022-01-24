![](https://docs.growthbook.io/images/hero.png)

# Growth Book - Android



- **Lightweight and fast**
- **Supports both Kotlin & JAVA based apps.**
- **Android SDK 26 & Above**
- **Use your existing event tracking (GA, Segment, Mixpanel, custom)**
- **Adjust variation weights and targeting without deploying new code**



## Installation

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.growthbook.sdk:GrowthBook:1.0.3'
}
```



## Integration

Integration is super easy:

1. Create a Growth Book API key
2. At the start of your app,  do SDK Initialization as per below

Now you can start/stop tests, adjust coverage and variation weights, and apply a winning variation to 100% of traffic, all within the Growth Book App without deploying code changes to your site.

```kotlin
GBSDKBuilder(apiKey = <API_KEY>,
    hostURL = <GrowthBook_URL>,
    attributes = <Hashmap>,
    trackingCallback = { gbExperiment, gbExperimentResult ->

}).initialize()
```

There are additional properties which can be setup at the time of initialization

```kotlin
GBSDKBuilder(apiKey = <API_KEY>,
    hostURL = <GrowthBook_URL>,
    attributes = <Hashmap>,
    trackingCallback = { gbExperiment, gbExperimentResult ->

})
    .setEnabled(true) // Enable / Disable experiments
    .setQAMode(true) // Enable / Disable QA Mode
    .setForcedVariations(<HashMap>) // Pass Forced Variations
    .setRefreshHandler { isRefreshed ->
        
    } // Get Callbacks when SDK refreshed its cache
    .setNetworkDispatcher(<Network Dispatcher>) // Pass Network cliet to be used for API Calls
    .initialize()
```



## Usage

- Initialization returns SDK instance - GrowthBookSDK

- The feature method takes a single string argument, which is the unique identifier for the feature and returns a FeatureResult object.

  ```kotlin
  fun feature(id: String) : GBFeatureResult
  ```

- The run method takes an Experiment object and returns an ExperimentResult

```kotlin
fun run(experiment: GBExperiment) : GBExperimentResult 
```



- Manually Refresh Cache

  ```kotlin
  fun refreshCache()
  ```

- Get Context

```kotlin
fun getGBContext() : GBContext
```

- Get Features

```kotlin
fun getFeatures() : GBFeatures
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
    val apiKey: String,
    /**
     * Host URL for GrowthBook
     */
    val hostURL : String,
    /**
     * Switch to globally disable all experiments. Default true.
     */
    val enabled : Boolean,
    /**
     * Map of user attributes that are used to assign variations
     */
    val attributes: HashMap<String, Any>,
    /**
     * Force specific experiments to always assign a specific variation (used for QA)
     */
    val forcedVariations: HashMap<String, Int>,
    /**
     * If true, random assignment is disabled and only explicitly forced variations are used.
     */
    val qaMode : Boolean,
    /**
     * A function that takes experiment and result as arguments.
     */
    val trackingCallback : (GBExperiment, GBExperimentResult) -> Unit
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
    val defaultValue : JsonElement? = null,
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
    val condition : GBCondition? = null,
    /**
     * What percent of users should be included in the experiment (between 0 and 1, inclusive)
     */
    val coverage : Float? = null,
    /**
     * Immediately force a specific value (ignore every other option besides condition and coverage)
     */
    val force : JsonElement? = null,
    /**
     * Run an experiment (A/B test) and randomly choose between these variations
     */
    val variations: ArrayList<JsonElement>? = null,
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
    val namespace : JsonArray? = null,
    /**
     * What user attribute should be used to assign variations (defaults to id)
     */
    val hashAttribute: String? = null
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
    experiment
}

/**
 * Result for Feature
 */
class GBFeatureResult(
    /**
     * The assigned value of the feature
     */
    val value : Any?,
    /**
     * The assigned value cast to a boolean
     */
    val on : Boolean = false,
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
    val variations : List<JsonElement> = ArrayList(),
    /**
     * A tuple that contains the namespace identifier, plus a range of coverage for the experiment
     */
    val namespace : JsonArray? = null,
    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    val hashAttribute: String? = null,
    /**
     * How to weight traffic between variations. Must add to 1.
     */
    var weights : List<Float>? = null,
    /**
     * If set to false, always return the control (first variation)
     */
    var active : Boolean = true,
    /**
     * What percent of users should be included in the experiment (between 0 and 1, inclusive)
     */
    var coverage : Float? = null,
    /**
     * Optional targeting condition
     */
    var condition: GBCondition? = null,
    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    var force : Int? = null
)

/**
 * The result of running an Experiment given a specific Context
 */
class GBExperimentResult(
    /**
     * Whether or not the user is part of the experiment
     */
    val inExperiment: Boolean,
    /**
     * The array index of the assigned variation
     */
    val variationId: Int,
    /**
     * The array value of the assigned variation
     */
    val value: Any,
    /**
     * The user attribute used to assign a variation
     */
    val hashAttribute: String? = null,
    /**
     * The value of that attribute
     */
    val hashValue: String? = null

)
```



## Proguard

If you're using ProGuard, you may need to add rules to your configuration file to make it compatible with Obfuscation & Shriniking tools. These rules are guidelines only and some projects require more to work. You can modify those rules and adapt them to your project, but be aware that we do not support custom rules.



```
# Core SDK
-keep class com.sdk.growthbook.** { *; }

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



## License

This project uses the MIT license. The core GrowthBook app will always remain open and free, although we may add some commercial enterprise add-ons in the future.