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
    implementation 'io.growthbook.sdk:GrowthBook:<version>' // 3.1.0 latest version when this file was edited

    // Add Network Dispatcher you prefer:
    // 1) NetworkDispatcherKtor artifact contains the Network Dispatcher based on Ktor artifact
    implementation 'io.growthbook.sdk:NetworkDispatcherKtor:1.0.4'
    // 2) NetworkDispatcherOkHttp artifact contains the Network Dispatcher based on OkHttp artifact
    implementation 'io.growthbook.sdk:NetworkDispatcherOkHttp:1.0.2'
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

## Changelog
- **v1.1.63** 2024-11-26
    - The type of `value` field of `GBFeatureResult` class was changed to `kotlinx.serialization.json.JsonElement`
- **v2.0.0-alpha** 2025-01-10
    - the `value` field was renamed to `gbValue`, the type of this field was changed to `GBValue`;
    - `inline fun <reified V>feature(id: String): V?` was added (useful when you need only feature value and you know the type of this feature)
- **v3.0.0-alpha** 2025-01-27
    - user attributes type was changed (map of GB values);
    - attributesOverride is map of GB values;
    - forced features is map of GB values
- **v4.0.0-alpha** 2025-03-03
    - `initialize()` method was changed from non-suspend to suspend method
in order to get rid of null on the first access. It is expected that user of
the SDK calls `initialize()` from coroutine. For those who doesn't use coroutines,
they can use `initializeWithoutWaitForCall()` method


## License

This project uses the MIT license. The core GrowthBook app will always remain open and free, although we may add some
commercial enterprise add-ons in the future.
 
