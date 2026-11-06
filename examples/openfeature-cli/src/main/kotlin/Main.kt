import com.sdk.growthbook.network.GBNetworkDispatcherOkHttp
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.openfeature.GrowthBookOpenFeatureProvider
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import dev.openfeature.kotlin.sdk.ImmutableContext
import dev.openfeature.kotlin.sdk.OpenFeatureAPI
import dev.openfeature.kotlin.sdk.Value
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/** Features used when no client key is given, so the example runs offline. */
const val BUNDLED_FEATURES = """
{
    "features": {
        "welcome-message": {
            "defaultValue": "Hello!",
            "rules": [{"condition": {"country": "CA"}, "force": "Bonjour!"}]
        },
        "dark-mode": {"defaultValue": false},
        "max-items": {"defaultValue": 10},
        "checkout-layout": {
            "defaultValue": "classic",
            "rules": [{
                "key": "checkout-layout",
                "variations": ["classic", "compact"],
                "weights": [0.5, 0.5],
                "coverage": 1,
                "hashAttribute": "id"
            }]
        }
    }
}
"""

/** Network dispatcher that answers every feature request with [BUNDLED_FEATURES]. */
class BundledFeaturesDispatcher : NetworkDispatcher {
    override fun consumeGETRequest(request: String, onSuccess: (String) -> Unit, onError: (Throwable) -> Unit): Job {
        onSuccess(BUNDLED_FEATURES)
        return Job().apply { complete() }
    }

    override fun consumeSSEConnection(url: String, sseController: SSEConnectionController?): Flow<Resource<String>> =
        emptyFlow()

    override fun consumePOSTRequest(
        url: String,
        bodyParams: Map<String, Any>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        onSuccess(BUNDLED_FEATURES)
    }
}

fun main(args: Array<String>) {
    runBlocking { run(args) }
    // OkHttp's idle threads would otherwise keep the JVM alive for a minute.
    exitProcess(0)
}

suspend fun run(args: Array<String>) {
    // Set GB_CLIENT_KEY to fetch live features; GB_API_HOST defaults to GrowthBook Cloud.
    val clientKey = System.getenv("GB_CLIENT_KEY")
    val apiHost = System.getenv("GB_API_HOST") ?: "https://cdn.growthbook.io"

    val provider = GrowthBookOpenFeatureProvider(
        apiKey = clientKey ?: "sdk-offline",
        apiHost = apiHost,
        networkDispatcher = if (clientKey == null) BundledFeaturesDispatcher() else GBNetworkDispatcherOkHttp(),
        trackingCallback = { experiment, result ->
            println("  [exposure] ${experiment.key} -> variation ${result.variationId}")
        },
    )

    OpenFeatureAPI.setProviderAndWait(
        provider,
        ImmutableContext(targetingKey = "user-123", attributes = mapOf("country" to Value.String("CA"))),
    )

    val client = OpenFeatureAPI.getClient()
    println(if (clientKey == null) "Using bundled features" else "Using features from $apiHost")

    if (clientKey == null) {
        println("welcome-message: ${client.getStringValue("welcome-message", "Hi")}")
        println("dark-mode: ${client.getBooleanValue("dark-mode", true)}")
        println("max-items: ${client.getIntegerValue("max-items", 0)}")

        val layout = client.getStringDetails("checkout-layout", "classic")
        println("checkout-layout: ${layout.value} (reason: ${layout.reason}, variant: ${layout.variant})")
    } else {
        // With live features, pass flag keys as arguments: ./gradlew -p examples/openfeature-cli run --args="my-flag other-flag"
        for (key in args) {
            val details = client.getObjectDetails(key, Value.Null)
            println("$key: ${details.value} (reason: ${details.reason})")
        }
    }

    provider.shutdown()
}
