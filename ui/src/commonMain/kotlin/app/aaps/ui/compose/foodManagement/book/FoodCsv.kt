package app.aaps.ui.compose.foodManagement.book

import app.aaps.core.data.model.FD
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.ui.UiStrings
import dev.zacsweers.metro.Inject
import kotlin.math.roundToInt

/**
 * The food data as two CSV files, for a spreadsheet: the meals eaten, and the foods, dishes and
 * standard meals. Same style as the user entries export: ";" between the values, text in quotes,
 * headers and decimals in the language of the app.
 *
 * PROOF OF CONCEPT. The files hold health data. They are saved where the user keeps the other AAPS
 * exports, and are not sent anywhere.
 */
@Inject
class FoodCsv(
    private val rh: TextResolver,
    private val dateUtil: DateUtil,
    private val decimalFormatter: DecimalFormatter,
    private val files: FoodExportFiles,
) {

    /**
     * Writes both files. Stops at the first one that cannot be saved.
     *
     * @param from only the meals eaten from this time are written, or all of them when null
     * @param given eaten meal id to the insulin really given for it
     */
    fun export(meals: List<EatenMeal>, links: Map<String, TreatmentLink>, given: Map<String, Double>, from: Long?, foods: List<FD>, recipes: List<Recipe>): FileResult {
        val first = files.save(HISTORY_FILE, MIME_TYPE, history(meals, links, given, from))
        if (first != FileResult.SAVED) return first
        return files.save(CATALOG_FILE, MIME_TYPE, catalog(foods, recipes))
    }

    /**
     * One line per food eaten. Only the meals whose treatment was found are written, as in the
     * history of the screen, at the time of that treatment.
     *
     * The first line of each meal also has the carbs of the treatment and the insulin really given
     * for it ([given], from the bolus records, not the calculation: the user can change or stop a
     * bolus). They are only on that line, so the sum of a column stays right. They stay empty when
     * not known; the insulin is always empty for a meal entered with the carbs dialog.
     */
    fun history(meals: List<EatenMeal>, links: Map<String, TreatmentLink>, given: Map<String, Double> = emptyMap(), from: Long? = null): String {
        val header = listOf(
            CoreUiStrings.date, CoreUiStrings.time, UiStrings.food_meal_title, UiStrings.food_csv_entered_with, CoreUiStrings.name,
            UiStrings.food_csv_quantity, CoreUiStrings.unit_label, CoreUiStrings.label_carbs_g, CoreUiStrings.label_fat_g,
            CoreUiStrings.label_protein_g, CoreUiStrings.label_energy_kj, UiStrings.food_csv_carbs_entered, UiStrings.food_csv_insulin_given
        )
        val rows = meals
            .mapNotNull { meal -> (meal.validatedAt ?: links[meal.id]?.timestamp)?.let { time -> meal to time } }
            .filter { (_, time) -> from == null || time >= from }
            .sortedBy { it.second }
            .flatMap { (meal, time) ->
                val link = links[meal.id]
                meal.items.mapIndexed { index, item ->
                    val first = index == 0
                    listOf(
                        dateUtil.dateString(time), dateUtil.timeString(time), text(meal.name.orEmpty()), text(enteredWith(meal.target)),
                        // A line typed by hand has no quantity: its unit column says the carbs are an estimate.
                        text(item.name), if (item.estimated) "" else number(item.quantity),
                        text(if (item.estimated) rh.gs(UiStrings.food_meal_estimated) else item.unit), number(item.carbs),
                        // Only exact values: a dish line with a value known for part of its foods is left empty.
                        number(item.nutrition.exact(Nutrient.FAT)), number(item.nutrition.exact(Nutrient.PROTEIN)), number(item.nutrition.exact(Nutrient.ENERGY)),
                        if (first) number(link?.carbs) else "",
                        if (first) given[meal.id]?.let { decimalFormatter.to2Decimal(it) }.orEmpty() else ""
                    )
                }
            }
        return table(header, rows)
    }

    /**
     * One line per food, dish and standard meal. The values of a dish or a meal are computed from
     * its foods, for one serving. They stay empty when a food in it does not exist any more.
     */
    fun catalog(foods: List<FD>, recipes: List<Recipe>): String {
        val header = listOf(
            UiStrings.food_csv_type, CoreUiStrings.name, CoreUiStrings.category, CoreUiStrings.subcategory, CoreUiStrings.portion,
            CoreUiStrings.unit_label, CoreUiStrings.label_carbs_g, CoreUiStrings.label_fat_g, CoreUiStrings.label_protein_g,
            CoreUiStrings.label_energy_kj, UiStrings.food_csv_contents
        )
        val foodRows = foods.sortedBy { it.name.lowercase() }.map { food ->
            listOf(
                text(rh.gs(UiStrings.food_csv_kind_food)), text(food.name), text(food.category.orEmpty()), text(food.subCategory.orEmpty()),
                number(food.portion), text(food.unit), number(food.carbs.toDouble()), number(food.fat?.toDouble()),
                number(food.protein?.toDouble()), number(food.energy?.toDouble()), ""
            )
        }
        val foodsById = foods.associateBy { it.id }
        val recipesById = recipes.associateBy { it.id }
        val recipeRows = recipes.sortedWith(compareBy({ it.kind }, { it.name.lowercase() })).map { recipe ->
            val nutrition = FoodBookMath.nutritionOf(recipe, foodsById, recipesById)
            listOf(
                text(rh.gs(if (recipe.kind == RecipeKind.DISH) UiStrings.food_book_kind_dish else UiStrings.food_book_kind_meal)),
                text(recipe.name), "", "", number(1.0), text(rh.gs(UiStrings.food_book_servings)), number(nutrition?.carbs),
                number(nutrition?.exact(Nutrient.FAT)), number(nutrition?.exact(Nutrient.PROTEIN)), number(nutrition?.exact(Nutrient.ENERGY)), text(contentsOf(recipe))
            )
        }
        return table(header, foodRows + recipeRows)
    }

    private fun enteredWith(target: MealTarget): String = rh.gs(if (target == MealTarget.WIZARD) CoreUiStrings.boluswizard else InterfacesStrings.carbs)

    /** "Bread 80 g, Milk 250 ml", with the same texts as the screen. */
    private fun contentsOf(recipe: Recipe): String =
        recipe.items
            .map { item ->
                val amount = item.estimatedCarbs?.let { rh.gs(UiStrings.food_book_estimated_item, rh.gs(InterfacesStrings.format_carbs, it.roundToInt())) }
                    ?: rh.gs(CoreUiStrings.value_with_unit, decimalFormatter.to1Decimal(item.quantity), item.unit)
                rh.gs(UiStrings.food_book_item, item.name, amount)
            }
            .reduceOrNull { acc, next -> rh.gs(UiStrings.food_meal_note_join, acc, next) }
            .orEmpty()

    private fun table(header: List<TextRef>, rows: List<List<String>>): String =
        (listOf(header.map { text(rh.gs(it)) }) + rows).joinToString(separator = "\n", postfix = "\n") { it.joinToString(SEPARATOR) }

    private fun number(value: Double?): String = value?.let { decimalFormatter.to1Decimal(it) }.orEmpty()

    internal companion object {

        const val SEPARATOR = ";"
        const val MIME_TYPE = "text/csv"
        const val HISTORY_FILE = "FoodHistory.csv"
        const val CATALOG_FILE = "FoodCatalog.csv"

        /** Text in quotes, a quote doubled, a new line replaced: a name can hold any of them. */
        fun text(value: String): String = if (value.isEmpty()) "" else "\"" + value.replace("\"", "\"\"").replace("\n", " / ") + "\""
    }
}
