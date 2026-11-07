package app.aaps.ui.compose.foodManagement.book

import app.aaps.core.data.model.FD
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class FoodCsvTest {

    /** Every header is "H"; a text with arguments is its arguments joined with a space. */
    private val rh = object : TextResolver {
        override fun gs(ref: TextRef): String = "H"
        override fun gs(ref: TextRef, vararg args: Any?): String = args.joinToString(" ")
        override fun gsNotLocalised(ref: TextRef): String = "H"
    }
    private val dateUtil: DateUtil = mock()
    private val decimalFormatter: DecimalFormatter = mock()
    private val files = RecordingFiles()
    private lateinit var sut: FoodCsv

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.dateString(any<Long>())).thenAnswer { "d${it.arguments[0]}" }
        whenever(dateUtil.timeString(any<Long>())).thenAnswer { "t${it.arguments[0]}" }
        whenever(decimalFormatter.to1Decimal(any<Double>())).thenAnswer { (it.arguments[0] as Double).toString() }
        whenever(decimalFormatter.to2Decimal(any<Double>())).thenAnswer { "%.2f".format(it.arguments[0] as Double) }
        sut = FoodCsv(rh, dateUtil, decimalFormatter, files)
    }

    private fun item(name: String, carbs: Double) = EatenItem(name, quantity = 100.0, unit = "g", carbs = carbs, fat = null, protein = 1.0, energyKj = null)

    private fun meal(id: String, validatedAt: Long?, vararg items: EatenItem) =
        EatenMeal(id = id, timestamp = 1L, name = null, note = "", wizardCarbs = 0, items = items.toList(), validatedAt = validatedAt)

    private val meals = listOf(
        meal("late", validatedAt = 2000L, item("Bread", 40.0), item("Milk", 12.0)),
        meal("waiting", validatedAt = null, item("Cake", 60.0)),
        meal("linked", validatedAt = null, item("Apple", 15.0)),
    )
    private val links = mapOf(
        "linked" to TreatmentLink(id = 7, timestamp = 1000L, insulin = null, carbs = 15.0, sameNote = true),
        "late" to TreatmentLink(id = 8, timestamp = 2000L, insulin = 3.25, carbs = 50.0, sameNote = true),
    )

    @Test
    fun `history has one line per food of the found meals, in time order`() {
        val lines = sut.history(meals, links).trimEnd().lines()

        assertThat(lines).hasSize(4)
        assertThat(lines[0].split(";")).hasSize(13)
        assertThat(lines[1]).startsWith("d1000;t1000;;")
        assertThat(lines[1]).contains("\"Apple\";100.0;\"g\";15.0;;1.0;")
        assertThat(lines[2]).contains("\"Bread\"")
        assertThat(lines[3]).contains("\"Milk\"")
        assertThat(lines.none { it.contains("Cake") }).isTrue()
    }

    @Test
    fun `carbs entered and insulin given are only on the first line of a meal`() {
        // The wizard calculated 3.25 U (in the link), but the bolus was stopped at 2.1 U.
        val lines = sut.history(meals, links, given = mapOf("late" to 2.1)).trimEnd().lines()

        val bread = lines[2].split(";")
        assertThat(bread[11]).isEqualTo("50.0")
        assertThat(bread[12]).isEqualTo("%.2f".format(2.1))
        val milk = lines[3].split(";")
        assertThat(milk[11]).isEmpty()
        assertThat(milk[12]).isEmpty()
        // A carbs entry has no insulin.
        assertThat(lines[1].split(";")[12]).isEmpty()
    }

    @Test
    fun `the period keeps only the meals from that time`() {
        val lines = sut.history(meals, links, from = 1500L).trimEnd().lines()

        assertThat(lines).hasSize(3)
        assertThat(lines.none { it.contains("Apple") }).isTrue()
    }

    @Test
    fun `text with quotes and new lines stays in one cell`() {
        assertThat(FoodCsv.text("Pain \"maison\"\nbio")).isEqualTo("\"Pain \"\"maison\"\" / bio\"")
        assertThat(FoodCsv.text("")).isEmpty()
    }

    @Test
    fun `catalog computes a dish from its foods and leaves an unknown total empty`() {
        val bread = FD(id = 1, name = "Bread", portion = 100.0, carbs = 50, unit = "g")
        val dishes = listOf(
            Recipe("a", "Toast", RecipeKind.DISH, listOf(RecipeItem("Bread", 40.0, "g", foodId = 1))),
            Recipe("b", "Lost", RecipeKind.DISH, listOf(RecipeItem("Gone", 10.0, "g", foodId = 99))),
        )

        val lines = sut.catalog(listOf(bread), dishes).trimEnd().lines()

        assertThat(lines).hasSize(4)
        assertThat(lines[1]).startsWith("\"H\";\"Bread\";;;100.0;\"g\";50.0;;;;")
        val lost = lines.single { it.contains("\"Lost\"") }.split(";")
        assertThat(lost[6]).isEmpty()
        assertThat(lost[10]).isEqualTo("\"Gone 10.0 g\"")
        val toast = lines.single { it.contains("\"Toast\"") }.split(";")
        assertThat(toast[6]).isEqualTo("20.0")
    }

    @Test
    fun `export stops when the first file cannot be saved`() {
        files.result = FileResult.NO_FOLDER

        val result = sut.export(emptyList(), emptyMap(), emptyMap(), null, emptyList(), emptyList())

        assertThat(result).isEqualTo(FileResult.NO_FOLDER)
        assertThat(files.names).containsExactly(FoodCsv.HISTORY_FILE)
    }

    @Test
    fun `export saves both files`() {
        val result = sut.export(emptyList(), emptyMap(), emptyMap(), null, emptyList(), emptyList())

        assertThat(result).isEqualTo(FileResult.SAVED)
        assertThat(files.names).containsExactly(FoodCsv.HISTORY_FILE, FoodCsv.CATALOG_FILE).inOrder()
    }

    private class RecordingFiles : NoFoodExportFiles() {

        var result = FileResult.SAVED
        val names = mutableListOf<String>()
        override fun save(name: String, mimeType: String, text: String): FileResult {
            names += name
            return result
        }
    }
}
