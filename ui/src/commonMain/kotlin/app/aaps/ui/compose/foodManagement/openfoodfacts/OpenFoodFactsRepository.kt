package app.aaps.ui.compose.foodManagement.openfoodfacts

import androidx.compose.ui.text.intl.Locale
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import kotlinx.coroutines.withContext

/**
 * Finds foods on Open Food Facts, by barcode or by name.
 *
 * PROOF OF CONCEPT. Nothing is stored here: a product only becomes an AAPS food when the user
 * checks the values in the food editor and saves it.
 *
 * Only the barcode or the search words are sent. Nothing about the user or their therapy is sent.
 */
@SingleIn(AppScope::class)
@Inject
class OpenFoodFactsRepository(
    private val receiverStatusStore: ReceiverStatusStore,
    private val aapsLogger: AAPSLogger
) {

    private val client = OpenFoodFactsClient(
        http = HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = TIMEOUT_MS
                requestTimeoutMillis = TIMEOUT_MS
            }
            // OFF asks every app to name itself, so its team can contact the authors if needed.
            install(UserAgent) { agent = USER_AGENT }
        },
        language = { Locale.current.language.lowercase() },
        onFailure = { reason -> aapsLogger.warn(LTag.HTTP, "Open Food Facts request failed: $reason") }
    )

    /**
     * Digits only is read as a barcode, anything else as search words.
     *
     * The request is always tried: the connection state can be a little late, so it is only used to
     * explain a failure, never to skip a request.
     */
    suspend fun lookup(query: String): OffResult {
        val text = query.trim()
        if (text.isEmpty()) return OffResult.NotFound
        val result = withContext(aapsIoDispatcher) {
            if (isBarcode(text)) client.productByBarcode(text) else client.search(text)
        }
        return if (result == OffResult.Failed && !receiverStatusStore.isConnected) OffResult.Offline else result
    }

    /** Downloads the photo at [url] (an address read from OFF). */
    suspend fun image(url: String): OffImage = withContext(aapsIoDispatcher) { client.image(url) }

    /**
     * Reads the product again, to get the address of its photo today: OFF changes the address
     * when a photo is replaced. A product OFF does not know any more has no photo.
     */
    suspend fun currentImageUrl(barcode: String): CurrentImage = withContext(aapsIoDispatcher) {
        when (val result = client.productByBarcode(barcode)) {
            is OffResult.Found -> result.products.firstOrNull()?.imageUrl?.let { CurrentImage.At(it) } ?: CurrentImage.None
            OffResult.NotFound -> CurrentImage.None
            else               -> CurrentImage.Unknown
        }
    }

    companion object {

        private const val TIMEOUT_MS = 15_000L

        // TODO before any release: OFF asks for "AppName/Version (contact e-mail)". The contact must be
        //  a project address chosen by the AAPS maintainers, never the e-mail of a user.
        private const val USER_AGENT = "AndroidAPS-FoodPOC/0.1 (https://github.com/nightscout/AndroidAPS)"

        /** EAN-8, UPC-A (12), EAN-13 and GTIN-14 are 8 to 14 digits. */
        fun isBarcode(text: String): Boolean = text.length in 8..14 && text.all { it in '0'..'9' }
    }
}
