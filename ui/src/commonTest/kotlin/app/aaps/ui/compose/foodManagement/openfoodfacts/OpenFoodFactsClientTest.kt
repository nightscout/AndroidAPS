package app.aaps.ui.compose.foodManagement.openfoodfacts

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Open Food Facts client against scripted answers. What matters is how loosely OFF data is read:
 * numbers as strings, missing values, localized names, and products without a name.
 */
class OpenFoodFactsClientTest {

    private val requests = mutableListOf<Url>()

    private fun client(status: HttpStatusCode = HttpStatusCode.OK, body: String): OpenFoodFactsClient {
        val engine = MockEngine { request ->
            requests += request.url
            respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return OpenFoodFactsClient(HttpClient(engine), language = { "fr" })
    }

    @Test
    fun `a barcode finds one product with its values per 100 g`() = runTest {
        val result = client(body = PRODUCT).productByBarcode("3017620422003")

        val product = assertIs<OffResult.Found>(result).products.single()
        assertEquals("3017620422003", product.barcode)
        assertEquals("Pâte à tartiner", product.name) // the French name wins over the main one
        assertEquals("Marque A", product.brand)        // first brand only
        assertEquals(57.5, product.carbsPer100)
        assertEquals(56.3, product.sugarsPer100)       // sent as a string by OFF
        assertNull(product.fiberPer100)                // missing stays missing
        assertEquals(2252.0, product.energyKjPer100)
        assertEquals(false, product.isLiquid)
        assertTrue(requests.single().encodedPath.endsWith("/api/v2/product/3017620422003"))
    }

    @Test
    fun `energy in kcal only is turned into kJ`() = runTest {
        val body = """{"status":1,"product":{"code":"1","product_name":"X","nutriments":{"energy-kcal_100g":100}}}"""
        val product = assertIs<OffResult.Found>(client(body = body).productByBarcode("12345678")).products.single()
        assertEquals(418.4, product.energyKjPer100!!, 0.001)
    }

    @Test
    fun `a liquid is read from the quantity unit`() = runTest {
        val body = """{"status":1,"product":{"code":"1","product_name":"Lait","product_quantity_unit":"ml","nutriments":{"carbohydrates_100g":4.8}}}"""
        val product = assertIs<OffResult.Found>(client(body = body).productByBarcode("12345678")).products.single()
        assertTrue(product.isLiquid)
    }

    @Test
    fun `an unknown barcode is not found`() = runTest {
        assertEquals(OffResult.NotFound, client(body = """{"status":0}""").productByBarcode("12345678"))
        assertEquals(OffResult.NotFound, client(HttpStatusCode.NotFound, """{"status":0}""").productByBarcode("12345678"))
    }

    @Test
    fun `a server error or broken json is a failure, not an empty answer`() = runTest {
        assertEquals(OffResult.Failed, client(HttpStatusCode.InternalServerError, "{}").productByBarcode("12345678"))
        assertEquals(OffResult.Failed, client(body = "not json").search("bread"))
    }

    @Test
    fun `a search skips products without a name`() = runTest {
        val body = """{"count":2,"products":[{"code":"1","nutriments":{}},{"code":"2","product_name":"Baguette","nutriments":{"carbohydrates_100g":"56"}}]}"""
        val products = assertIs<OffResult.Found>(client(body = body).search("baguette")).products
        assertEquals(listOf("Baguette"), products.map { it.name })
        assertEquals(56.0, products.single().carbsPer100)
        assertEquals("baguette", requests.single().parameters["search_terms"])
    }

    @Test
    fun `a search with no product is not found`() = runTest {
        assertEquals(OffResult.NotFound, client(body = """{"count":0,"products":[]}""").search("zzz"))
    }

    @Test
    fun `a short failure is tried again, without the user seeing it`() = runTest {
        val statuses = ArrayDeque(listOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.OK))
        val engine = MockEngine { request ->
            requests += request.url
            respond(ByteReadChannel(PRODUCT), statuses.removeFirst(), headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val result = OpenFoodFactsClient(HttpClient(engine), language = { "fr" }).productByBarcode("3017620422003")

        assertIs<OffResult.Found>(result)
        assertEquals(2, requests.size)
    }

    @Test
    fun `a failure that lasts is reported after the retries`() = runTest {
        assertEquals(OffResult.Failed, client(HttpStatusCode.ServiceUnavailable, "{}").search("bread"))
        assertEquals(3, requests.size) // first try and two retries
    }

    @Test
    fun `too many requests is never retried`() = runTest {
        assertEquals(OffResult.RateLimited, client(HttpStatusCode.TooManyRequests, "{}").search("bread"))
        assertEquals(1, requests.size)
    }

    @Test
    fun `the serving written on the package is read`() = runTest {
        val body = """{"status":1,"product":{"code":"1","product_name":"Yaourt","serving_size":"1 pot (125 g)","serving_quantity":"125","nutriments":{}}}"""
        val product = assertIs<OffResult.Found>(client(body = body).productByBarcode("12345678")).products.single()
        assertEquals("1 pot (125 g)", product.servingSize)
        assertEquals(125.0, product.servingQuantity)
    }

    @Test
    fun `the photo address is read, and only an openfoodfacts address is kept`() = runTest {
        val url = "https://images.openfoodfacts.org/images/products/1/front_fr.3.200.jpg"
        val good = """{"status":1,"product":{"code":"1","product_name":"Yaourt","image_front_small_url":"$url","nutriments":{}}}"""
        assertEquals(url, assertIs<OffResult.Found>(client(body = good).productByBarcode("12345678")).products.single().imageUrl)

        val other = """{"status":1,"product":{"code":"1","product_name":"Yaourt","image_front_small_url":"https://example.com/a.jpg","nutriments":{}}}"""
        assertNull(assertIs<OffResult.Found>(client(body = other).productByBarcode("12345678")).products.single().imageUrl)

        val none = """{"status":1,"product":{"code":"1","product_name":"Yaourt","nutriments":{}}}"""
        assertNull(assertIs<OffResult.Found>(client(body = none).productByBarcode("12345678")).products.single().imageUrl)
    }

    @Test
    fun `only an https address on openfoodfacts is downloaded`() = runTest {
        assertTrue(OpenFoodFactsClient.isOffImageUrl("https://images.openfoodfacts.org/images/products/1/front.200.jpg"))
        assertTrue(OpenFoodFactsClient.isOffImageUrl("https://openfoodfacts.org/a.jpg"))
        assertEquals(false, OpenFoodFactsClient.isOffImageUrl("http://images.openfoodfacts.org/a.jpg"))
        assertEquals(false, OpenFoodFactsClient.isOffImageUrl("https://openfoodfacts.org.example.com/a.jpg"))
        assertEquals(false, OpenFoodFactsClient.isOffImageUrl("https://example.com/openfoodfacts.org/a.jpg"))

        assertEquals(OffImage.Missing, client(body = "x").image("https://example.com/a.jpg"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a photo is downloaded, a removed one is missing, a server error is a failure`() = runTest {
        val url = "https://images.openfoodfacts.org/images/products/1/front_fr.3.200.jpg"
        assertEquals("jpg", assertIs<OffImage.Found>(client(body = "jpg").image(url)).bytes.decodeToString())
        assertEquals(OffImage.Missing, client(HttpStatusCode.NotFound, "").image(url))
        assertEquals(OffImage.Failed, client(HttpStatusCode.InternalServerError, "").image(url))
    }

    @Test
    fun `only digits of the right length are a barcode`() {
        assertTrue(OpenFoodFactsRepository.isBarcode("3017620422003"))
        assertTrue(OpenFoodFactsRepository.isBarcode("12345678"))
        assertEquals(false, OpenFoodFactsRepository.isBarcode("1234567"))
        assertEquals(false, OpenFoodFactsRepository.isBarcode("301762042200a"))
        assertEquals(false, OpenFoodFactsRepository.isBarcode("../../api"))
    }

    private companion object {

        const val PRODUCT = """
            {"status":1,"product":{
              "code":"3017620422003",
              "product_name":"Hazelnut spread",
              "product_name_fr":"Pâte à tartiner",
              "brands":"Marque A, Marque B",
              "product_quantity_unit":"g",
              "nutriments":{"carbohydrates_100g":57.5,"sugars_100g":"56.3","fat_100g":30.9,"proteins_100g":6.3,"energy-kj_100g":2252}
            }}"""
    }
}
