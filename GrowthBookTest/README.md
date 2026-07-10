# GrowthBookTest

In-memory test doubles for the GrowthBook Kotlin SDK. Depend on this module in
your **test** source set to unit-test feature-flag logic without a network, cache,
or real API key.

```kotlin
// build.gradle.kts (test dependencies)
implementation("io.growthbook.sdk:GrowthBookTest:<version>")
```

## `IGrowthBookSDK`

The core SDK exposes an interface, `IGrowthBookSDK`, implemented by both the real
`GrowthBookSDK` and `FakeGrowthBook`. Have your app code depend on the interface
so tests can swap in the fake:

```kotlin
class HomeViewModel(private val gb: IGrowthBookSDK) {
    fun title() = if (gb.isOn("new-home")) "New!" else "Welcome"
}

// production
HomeViewModel(realGrowthBookSdk)
// test
HomeViewModel(FakeGrowthBook().enable("new-home"))
```

## `FakeGrowthBook`

A deterministic, in-memory `IGrowthBookSDK`. Feature values are exactly what you
set; unset features evaluate as unknown (off, `null`).

### Feature overrides (per test)

```kotlin
val gb = FakeGrowthBook()
    .enable("new-home")               // boolean true
    .disable("promo-banner")          // boolean false
    .setValue("welcome-copy", "Hi")   // string
    .setValue("max-items", 25)        // number
    .setValue("config", GBNull)       // raw GBValue

gb.isOn("new-home")                   // true
gb.featureValue<String>("welcome-copy")
```

Truthiness matches the reference SDK: `null`, JSON `null`, `false`, `0`, and the
empty string are all **off**.

### Fixtures & reusable scenarios

Bulk-seed with `setFeatures`, and fork a shared fixture per test with `copy()`
so tests never share mutable state:

```kotlin
object GBScenarios {
    fun checkoutV2() = FakeGrowthBook()
        .setFeatures(mapOf("checkout-v2" to GBBoolean(true)))
        .setValue("max-items", 25)
}

val gb = GBScenarios.checkoutV2().copy().disable("promo-banner")
```

Or load a real payload exported from the GrowthBook dashboard (only each
feature's `defaultValue` is used):

```kotlin
val gb = FakeGrowthBook.fromFeaturesJson(exportedJson)
```

### Deterministic experiments

`run` returns the control (variation 0, `inExperiment = false`) unless you force
a variation:

```kotlin
val gb = FakeGrowthBook().setForcedVariation("exp", 1)
gb.run(experiment).variationId   // 1, inExperiment = true
```

### Interaction assertions

```kotlin
gb.wasQueried("new-home")   // was the flag consulted?
gb.queriedFeatures()        // keys queried, in call order
```

## Scope

`FakeGrowthBook` is a **pure override map**. It intentionally does **not**
evaluate rules, conditions, prerequisites, or attribute targeting — that keeps
tests deterministic. To exercise real targeting logic, use the real
`GrowthBookSDK` with a mock network dispatcher instead.
