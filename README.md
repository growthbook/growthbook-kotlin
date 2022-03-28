![](https://docs.growthbook.io/images/hero.png)

# Growth Book - SDK

![](https://camo.githubusercontent.com/b1d9ad56ab51c4ad1417e9a5ad2a8fe63bcc4755e584ec7defef83755c23f923/687474703a2f2f696d672e736869656c64732e696f2f62616467652f706c6174666f726d2d616e64726f69642d3645444238442e7376673f7374796c653d666c6174) ![](https://camo.githubusercontent.com/1fec6f0d044c5e1d73656bfceed9a78fd4121b17e82a2705d2a47f6fd1f0e3e5/687474703a2f2f696d672e736869656c64732e696f2f62616467652f706c6174666f726d2d696f732d4344434443442e7376673f7374796c653d666c6174) ![](https://camo.githubusercontent.com/4ac08d7fb1bcb8ef26388cd2bf53b49626e1ab7cbda581162a946dd43e6a2726/687474703a2f2f696d672e736869656c64732e696f2f62616467652f706c6174666f726d2d74766f732d3830383038302e7376673f7374796c653d666c6174) ![](https://camo.githubusercontent.com/135dbadae40f9cabe7a3a040f9380fb485cff36c90909f3c1ae36b81c304426b/687474703a2f2f696d672e736869656c64732e696f2f62616467652f706c6174666f726d2d77617463686f732d4330433043302e7376673f7374796c653d666c6174)

![](https://maven-badges.herokuapp.com/maven-central/io.growthbook.sdk/GrowthBook/badge.svg)![Carthage compatible](https://img.shields.io/badge/Carthage-compatible-4BC51D.svg?style=flat)![](https://img.shields.io/badge/Swift_Package_Manager-compatible-orange?style=flat-square)![](https://img.shields.io/cocoapods/v/GrowthBook.svg)




- **Lightweight and fast**
- **Supports iOS & Android apps**
  - **Android version 21 & above**
  - **iOS version 12 & Above**
  - **Apple TvOS version 13 & Above**
  - **Apple WatchOS version 7 & Above**
- **Use your existing event tracking (GA, Segment, Mixpanel, custom)**
- **Adjust variation weights and targeting without deploying new code**



## Installation

##### Android 

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.growthbook.sdk:GrowthBook:<version>'
}
```

##### iOS

###### CocoaPods 

[CocoaPods](https://cocoapods.org/) is a dependency manager for Cocoa projects. For usage and installation instructions, visit their website. To integrate Alamofire into your Xcode project using CocoaPods, specify it in your `Podfile`:

- Add below line in your podfile, if not there

  - ```
    source 'https://github.com/CocoaPods/Specs.git'
    ```

- Add below in podfile - in respective target block

```kotlin
pod 'GrowthBook'
```

- Execute below command in terminal

  ```swift
  pod install --repo-update
  ```

###### Swift Package Manager - SPM

The [Swift Package Manager](https://swift.org/package-manager/) is a tool for automating the distribution of Swift code and is integrated into the `swift`compiler.

Once you have your Swift package set up, adding GrowthBook as a dependency is as easy as adding it to the `dependencies` value of your `Package.swift`.

```swift
dependencies: [
    .package(url: "https://github.com/growthbook/growthbook-kotlin.git")
]
```

###### Carthage (min Carthage version 0.38.0)

[Carthage](https://github.com/Carthage/Carthage) is a decentralized dependency manager that builds your dependencies and provides you with binary frameworks. To integrate Alamofire into your Xcode project using Carthage, specify it in your `Cartfile`:

```swift
binary "https://github.com/growthbook/growthbook-kotlin/blob/main/Carthage/GrowthBook.json"
```

```swift
carthage update --use-xcframework
```


## Integration

Integration is super easy:

1. Create a Growth Book API key
2. At the start of your app,  do SDK Initialization as per below

Now you can start/stop tests, adjust coverage and variation weights, and apply a winning variation to 100% of traffic, all within the Growth Book App without deploying code changes to your site.

```kotlin
var sdkInstance: GrowthBookSDK = GBSDKBuilderApp(apiKey = <API_KEY>,
    hostURL = <GrowthBook_URL>,
    attributes = <Hashmap>,
    trackingCallback = { gbExperiment, gbExperimentResult ->

}).initialize()
```

There are additional properties which can be setup at the time of initialization

```kotlin
var sdkInstance: GrowthBookSDK = GBSDKBuilderApp(apiKey = <API_KEY>,
    hostURL = <GrowthBook_URL>,
    attributes = <Hashmap>,
    trackingCallback = { gbExperiment, gbExperimentResult ->

})
		.setRefreshHandler { isRefreshed ->
        
    } // Get Callbacks when SDK refreshed its cache
    .setNetworkDispatcher(<Network Dispatcher>) // Pass Network cliet to be used for API Calls
    .setEnabled(true) // Enable / Disable experiments
    .setQAMode(true) // Enable / Disable QA Mode
    .setForcedVariations(<HashMap>) // Pass Forced Variations
    .initialize()
```



## Usage

- Initialization returns SDK instance - GrowthBookSDK

  ###### Use sdkInstance to consume below features -

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


## Proguard (Android)

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