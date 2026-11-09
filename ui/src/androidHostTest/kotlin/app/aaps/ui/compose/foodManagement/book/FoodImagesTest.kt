package app.aaps.ui.compose.foodManagement.book

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.ui.compose.foodManagement.openfoodfacts.CurrentImage
import app.aaps.ui.compose.foodManagement.openfoodfacts.OffImage
import app.aaps.ui.compose.foodManagement.openfoodfacts.OffProduct
import app.aaps.ui.compose.foodManagement.openfoodfacts.OpenFoodFactsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * The photo of an imported food is downloaded again when its file is missing, but only when OFF
 * has one: a product without a photo is never asked again.
 */
internal class FoodImagesTest {

    @Mock private lateinit var openFoodFacts: OpenFoodFactsRepository
    @Mock private lateinit var aapsLogger: AAPSLogger

    private val photo = byteArrayOf(1, 2, 3)
    private val newPhoto = byteArrayOf(4, 5, 6)
    private lateinit var store: MemoryImageStore
    private lateinit var book: FoodBookRepository
    private lateinit var sut: FoodImages

    private class MemoryImageStore : FoodImageStore {

        val files = mutableMapOf<String, ByteArray>()
        override val available: Boolean = true
        override fun read(barcode: String): ByteArray? = files[barcode]
        override fun write(barcode: String, bytes: ByteArray) {
            files[barcode] = bytes
        }
    }

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        store = MemoryImageStore()
        book = FoodBookRepository(MemoryFoodBookStore(), aapsLogger)
        sut = FoodImages(store, openFoodFacts, book, aapsLogger)
    }

    private fun product(imageUrl: String?) = OffProduct(
        barcode = BARCODE, name = "Yogurt", brand = null, carbsPer100 = 12.0, sugarsPer100 = null, fiberPer100 = null,
        fatPer100 = null, proteinPer100 = null, energyKjPer100 = null, isLiquid = false, imageUrl = imageUrl
    )

    private fun link(hasImage: Boolean = true, imageUrl: String? = OLD_URL) = OffLink(FOOD_ID, BARCODE, hasImage, imageUrl)

    @Test
    fun `a saved photo is read from the file, nothing is downloaded`() = runTest {
        book.saveOffLink(link())
        store.files[BARCODE] = photo

        assertThat(sut.forFood(FOOD_ID)).isEqualTo(photo)
        verifyNoInteractions(openFoodFacts)
    }

    @Test
    fun `a product without a photo is never asked again`() = runTest {
        book.saveOffLink(link(hasImage = false, imageUrl = null))

        assertThat(sut.forFood(FOOD_ID)).isNull()
        verifyNoInteractions(openFoodFacts)
    }

    @Test
    fun `a food typed by hand has no photo and asks nothing`() = runTest {
        assertThat(sut.forFood(FOOD_ID)).isNull()
        verifyNoInteractions(openFoodFacts)
    }

    @Test
    fun `a missing photo is downloaded again and saved`() = runTest {
        book.saveOffLink(link())
        whenever(openFoodFacts.image(OLD_URL)).thenReturn(OffImage.Found(photo))

        assertThat(sut.forFood(FOOD_ID)).isEqualTo(photo)
        assertThat(store.files[BARCODE]).isEqualTo(photo)
        verify(openFoodFacts, never()).currentImageUrl(any())
    }

    @Test
    fun `a replaced photo is found at its new address`() = runTest {
        book.saveOffLink(link())
        whenever(openFoodFacts.image(OLD_URL)).thenReturn(OffImage.Missing)
        whenever(openFoodFacts.currentImageUrl(BARCODE)).thenReturn(CurrentImage.At(NEW_URL))
        whenever(openFoodFacts.image(NEW_URL)).thenReturn(OffImage.Found(newPhoto))

        assertThat(sut.forFood(FOOD_ID)).isEqualTo(newPhoto)
        assertThat(store.files[BARCODE]).isEqualTo(newPhoto)
        assertThat(book.book.value.offLinks.single().imageUrl).isEqualTo(NEW_URL)
    }

    @Test
    fun `a photo removed on OFF is marked, so it is not asked again`() = runTest {
        book.saveOffLink(link())
        whenever(openFoodFacts.image(OLD_URL)).thenReturn(OffImage.Missing)
        whenever(openFoodFacts.currentImageUrl(BARCODE)).thenReturn(CurrentImage.None)

        assertThat(sut.forFood(FOOD_ID)).isNull()
        assertThat(book.book.value.offLinks.single().hasImage).isFalse()
    }

    @Test
    fun `no answer from OFF changes nothing, so it is tried again next time`() = runTest {
        book.saveOffLink(link())
        whenever(openFoodFacts.image(OLD_URL)).thenReturn(OffImage.Failed)

        assertThat(sut.forFood(FOOD_ID)).isNull()
        assertThat(book.book.value.offLinks.single()).isEqualTo(link())
        verify(openFoodFacts, never()).currentImageUrl(any())
    }

    @Test
    fun `a saved import keeps its product and the photo already downloaded`() = runTest {
        sut.link(FOOD_ID, product(OLD_URL), photo)

        assertThat(book.book.value.offLinks.single()).isEqualTo(link())
        assertThat(store.files[BARCODE]).isEqualTo(photo)
        verifyNoInteractions(openFoodFacts)
    }

    @Test
    fun `a product without a photo is saved as such`() = runTest {
        sut.link(FOOD_ID, product(imageUrl = null), null)

        assertThat(book.book.value.offLinks.single().hasImage).isFalse()
        assertThat(store.files).isEmpty()
        verifyNoInteractions(openFoodFacts)
    }

    private companion object {

        const val FOOD_ID = 7L
        const val BARCODE = "3017620422003"
        const val OLD_URL = "https://images.openfoodfacts.org/images/products/301/762/042/2003/front_fr.1.200.jpg"
        const val NEW_URL = "https://images.openfoodfacts.org/images/products/301/762/042/2003/front_fr.2.200.jpg"
    }
}
