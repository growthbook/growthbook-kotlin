# OpenFeature command-line example

A small JVM program that evaluates GrowthBook flags through the OpenFeature Kotlin SDK, using the `GrowthBookOpenFeature` provider from this repo.

Run it offline with bundled features:

```sh
./gradlew -p examples/openfeature-cli run
```

Or fetch live features from GrowthBook and evaluate the flags you name:

```sh
GB_CLIENT_KEY=sdk-abc123 ./gradlew -p examples/openfeature-cli run --args="my-flag other-flag"
```

Set `GB_API_HOST` to use a self-hosted GrowthBook instead of `https://cdn.growthbook.io`.

Run the commands from the repo root. The example builds the provider from source through a Gradle composite build; apps depend on `io.growthbook.sdk:GrowthBookOpenFeature` instead.
