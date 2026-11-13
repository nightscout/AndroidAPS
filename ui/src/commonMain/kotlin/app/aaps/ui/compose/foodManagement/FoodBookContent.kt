package app.aaps.ui.compose.foodManagement

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.aaps.core.data.model.BCR
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.foodManagement.book.EatenMeal
import app.aaps.ui.compose.foodManagement.book.ExportFile
import app.aaps.ui.compose.foodManagement.book.MealTarget
import app.aaps.ui.compose.foodManagement.book.Nutrient
import app.aaps.ui.compose.foodManagement.book.Nutrition
import app.aaps.ui.compose.foodManagement.book.Recipe
import app.aaps.ui.compose.foodManagement.book.RecipeKind
import app.aaps.ui.compose.foodManagement.book.TreatmentLink
import kotlin.math.roundToInt

// PROOF OF CONCEPT 2 and 3: dishes, standard meals and the meals eaten. Kept apart from the food
// screen so the experiment is easy to see, review and throw away.
//
// One rule on every list: a tap on a card adds it to the meal, the pencil changes it, and the
// orange and green buttons enter it at once with the carbs dialog or the bolus wizard. A long press
// selects cards to delete them with the button of the top bar, as in the treatments history.

@Composable
internal fun FoodTabsRow(selected: FoodTab, onSelect: (FoodTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selected.ordinal) {
        FoodTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        stringResource(
                            when (tab) {
                                FoodTab.FOODS   -> UiStrings.food_book_tab_foods
                                FoodTab.DISHES  -> UiStrings.food_book_tab_dishes
                                FoodTab.MEALS   -> UiStrings.food_book_tab_meals
                                FoodTab.HISTORY -> CoreUiStrings.history
                            }
                        )
                    )
                }
            )
        }
    }
}

/**
 * The saved dishes or standard meals. A tap adds one to the meal, a long press selects it.
 *
 * @param editing true while a dish or a meal is changed: then a tap adds to it, and the buttons that
 *   would start another action are hidden
 * @param selecting true while cards are selected to be deleted: then a tap selects or unselects
 */
@Composable
internal fun RecipeList(
    recipes: List<Recipe>,
    kind: RecipeKind,
    editing: Boolean,
    selecting: Boolean,
    selectedIds: Set<String>,
    nutritionOf: (Recipe) -> Nutrition?,
    problemOf: (Recipe) -> LineProblem? = { null },
    photos: Map<String, ByteArray> = emptyMap(),
    onAdd: (Recipe) -> Unit,
    onSelect: (Recipe) -> Unit,
    onToggle: (Recipe) -> Unit,
    onEdit: (Recipe) -> Unit,
    onCarbs: (Recipe) -> Unit,
    onCalculate: (Recipe) -> Unit,
) {
    val shown = recipes.filter { it.kind == kind }
    if (shown.isEmpty()) {
        EmptyText(stringResource(if (kind == RecipeKind.DISH) UiStrings.food_book_no_dishes else UiStrings.food_book_no_meals))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = AapsSpacing.medium, bottom = AapsSpacing.fabClearance),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        items(shown, key = { it.id }) { recipe ->
            val nutrition = nutritionOf(recipe)
            SelectableCard(
                selecting = selecting,
                selected = recipe.id in selectedIds,
                onClick = { if (selecting) onToggle(recipe) else onAdd(recipe) },
                // No delete while a dish is changed: the long press does nothing then.
                onLongClick = { if (selecting) onToggle(recipe) else if (!editing) onSelect(recipe) },
                actions = { if (!editing) CardActions(name = recipe.name, onEdit = { onEdit(recipe) }, onCarbs = { onCarbs(recipe) }, onCalculate = { onCalculate(recipe) }) },
            ) {
                recipe.photo?.let { photos[it] }?.let {
                    FoodPhoto(bytes = it, size = AapsSpacing.foodThumbnailSize)
                    Spacer(modifier = Modifier.width(AapsSpacing.medium))
                }
                CardTexts(
                    name = recipe.name,
                    detail = joinLines(recipe.items.map { item ->
                        val estimated = item.estimatedCarbs
                        val amount =
                            if (estimated != null) stringResource(UiStrings.food_book_estimated_item, stringResource(InterfacesStrings.format_carbs, estimated.roundToInt()))
                            else stringResource(CoreUiStrings.value_with_unit, MealMath.formatAmount(item.quantity), item.unit)
                        stringResource(UiStrings.food_book_item, item.name, amount)
                    }),
                    detailLines = 2,
                    extra = nutrition?.let { nutritionText(it) }.orEmpty(),
                )
                CardValues(
                    portion = null,
                    carbs = nutrition?.let { stringResource(InterfacesStrings.format_carbs, it.carbs.roundToInt()) }
                        ?: stringResource(if (problemOf(recipe) == LineProblem.UNIT_CHANGED) UiStrings.food_book_unit_changed_total else UiStrings.food_book_unknown_total),
                    carbsKnown = nutrition != null,
                )
            }
        }
    }
}

/**
 * A card of the food, dish and meal lists. A tap and a long press act on the whole card. While
 * cards are selected, a check box takes the place of the [actions].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SelectableCard(
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.extraLarge),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    // A screen reader says "double-tap and hold to Remove", as in the treatments.
                    onLongClickLabel = stringResource(CoreUiStrings.remove)
                )
                .padding(start = AapsSpacing.large, top = AapsSpacing.medium, bottom = AapsSpacing.medium, end = AapsSpacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
            if (selecting) Checkbox(checked = selected, onCheckedChange = { onClick() })
            else actions()
        }
    }
}

/** First column of a card: the name on up to 2 lines, and a detail line below. It takes all the free width. */
@Composable
internal fun RowScope.CardTexts(name: String, detail: String, detailLines: Int = 1, extra: String = "") {
    Column(modifier = Modifier.weight(1f)) {
        Text(text = name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        listOf(detail to detailLines, extra to 1).forEach { (text, lines) ->
            if (text.isNotEmpty()) Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * "F: 12 g, P: >8 g, 1500 kJ": fat, protein and energy. A value known for only part of the foods is
 * shown with ">": the real value is at least this one. A value not known, or 0, is not shown.
 */
@Composable
internal fun nutritionText(nutrition: Nutrition): String {
    val grams = stringResource(CoreUiStrings.units_grams)
    val parts = buildList {
        Nutrient.entries.forEach { nutrient ->
            val value = nutrition.valueOf(nutrient)?.takeIf { it > 0 } ?: return@forEach
            val number = if (nutrient == Nutrient.ENERGY) value.roundToInt().toString() else MealMath.formatAmount(value)
            val shown = if (nutrient in nutrition.partial) stringResource(UiStrings.food_value_at_least, number) else number
            // F and P were never translated; only the separator and the unit are.
            add(
                when (nutrient) {
                    Nutrient.FAT     -> stringResource(InterfacesStrings.confirmation_line, "F", stringResource(CoreUiStrings.value_with_unit, shown, grams))
                    Nutrient.PROTEIN -> stringResource(InterfacesStrings.confirmation_line, "P", stringResource(CoreUiStrings.value_with_unit, shown, grams))
                    Nutrient.ENERGY  -> stringResource(CoreUiStrings.value_with_unit, shown, "kJ")
                }
            )
        }
    }
    return joinLines(parts)
}

/**
 * The photo of a food, a dish or a meal, with the buttons to take one with the camera or to remove
 * it. The camera opens in place.
 *
 * @param shown the photo shown, or null
 * @param canRemove true when [shown] is a photo of the user (an OFF photo cannot be removed)
 */
@Composable
internal fun ColumnScope.UserPhotoSection(
    shown: ByteArray?,
    canRemove: Boolean,
    showCamera: Boolean,
    onOpenCamera: () -> Unit,
    onCloseCamera: () -> Unit,
    onPhoto: (ByteArray) -> Unit,
    onRemove: () -> Unit,
) {
    shown?.let { FoodPhoto(bytes = it, size = AapsSpacing.foodPhotoSize, modifier = Modifier.align(Alignment.CenterHorizontally)) }
    if (!foodPhotoCameraAvailable) return
    if (showCamera) {
        FoodPhotoCamera(
            onPhoto = onPhoto,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
        )
        TextButton(onClick = onCloseCamera, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(CoreUiStrings.cancel))
        }
    } else Row(modifier = Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        TextButton(onClick = onOpenCamera) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(Modifier.width(AapsSpacing.small))
            Text(stringResource(UiStrings.food_photo_take))
        }
        if (canRemove) TextButton(onClick = onRemove) {
            Text(stringResource(UiStrings.food_photo_remove))
        }
    }
}

/** Second column of a card, narrow: the portion above (cut with "..." when long), the carbs below. */
@Composable
internal fun CardValues(portion: String?, carbs: String, carbsKnown: Boolean = true) {
    Column(
        modifier = Modifier
            .widthIn(max = AapsSpacing.foodValueMaxWidth)
            .padding(start = AapsSpacing.medium, end = AapsSpacing.small),
        horizontalAlignment = Alignment.End
    ) {
        if (portion != null) {
            Text(
                text = portion,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = carbs,
            style = MaterialTheme.typography.titleMedium,
            color = if (carbsKnown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            textAlign = TextAlign.End
        )
    }
}

/**
 * The buttons of a food, dish or meal card: change it, enter it as carbs only (orange), or open the
 * bolus wizard with it (green). The colours and icons are the ones used for these actions everywhere.
 */
@Composable
internal fun CardActions(name: String, onEdit: () -> Unit, onCarbs: () -> Unit, onCalculate: () -> Unit) {
    // Every card has the same buttons: name the item on each, so a screen reader can tell them apart.
    CardIcon(Icons.Filled.Edit, stringResource(UiStrings.food_book_edit), name, MaterialTheme.colorScheme.onSurfaceVariant, onEdit)
    CardIcon(ElementType.CARBS.icon(), stringResource(InterfacesStrings.carbs), name, ElementType.CARBS.color(), onCarbs)
    CardIcon(ElementType.BOLUS_WIZARD.icon(), stringResource(CoreUiStrings.boluswizard), name, ElementType.BOLUS_WIZARD.color(), onCalculate)
}

@Composable
private fun CardIcon(icon: ImageVector, label: String, name: String, tint: Color, onClick: () -> Unit) {
    // The name goes on the button and the action on the icon: a screen reader says "Bread, Carbs".
    IconButton(onClick = onClick, modifier = Modifier.size(AapsSpacing.compactIconButtonSize).semantics { contentDescription = name }) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(AapsSpacing.chipIconSize), tint = tint)
    }
}

/** "Bread 80 g, Milk 250 ml". */
@Composable
internal fun joinLines(lines: List<String>): String =
    lines.reduceOrNull { acc, next -> stringResource(UiStrings.food_meal_note_join, acc, next) }
        .orEmpty()

/** The meals entered from this screen, newest first, each with the treatment found for it. */
@Composable
internal fun EatenMealList(
    meals: List<EatenMeal>,
    links: Map<String, TreatmentLink>,
    timeText: (Long) -> String,
    onShowCalculation: (BCR) -> Unit,
    images: Map<Long, ByteArray> = emptyMap(),
    photos: Map<String, ByteArray> = emptyMap(),
) {
    var detail by remember { mutableStateOf<EatenMeal?>(null) }
    detail?.let { meal -> EatenMealDetail(meal, images, photos, timeText, onDismiss = { detail = null }) }
    if (meals.isEmpty()) {
        EmptyText(stringResource(UiStrings.food_book_no_history))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = AapsSpacing.medium, bottom = AapsSpacing.fabClearance),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
    ) {
        item {
            Text(
                text = stringResource(UiStrings.food_book_history_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge)
            )
        }
        items(meals, key = { it.id }) { meal ->
            EatenMealCard(meal, links[meal.id], meal.photo?.let { photos[it] }, timeText, onShowCalculation = onShowCalculation, onShowDetail = { detail = meal })
        }
    }
}

@Composable
private fun EatenMealCard(meal: EatenMeal, link: TreatmentLink?, photo: ByteArray?, timeText: (Long) -> String, onShowCalculation: (BCR) -> Unit, onShowDetail: () -> Unit) {
    // A saved dish or meal sent as it is shows its name only, the lines are folded. A meal put
    // together on the spot, or a saved meal changed before it was sent, shows its lines.
    val foldable = meal.name != null && !meal.changed
    var expanded by rememberSaveable(meal.id) { mutableStateOf(!foldable) }
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.extraLarge)
            .clickable(onClick = onShowDetail),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(start = AapsSpacing.large, top = AapsSpacing.medium, bottom = AapsSpacing.medium, end = AapsSpacing.extraSmall),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            val type = if (meal.target == MealTarget.WIZARD) ElementType.BOLUS_WIZARD else ElementType.CARBS
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The photo of the dish or meal sent, as on the other tabs. The photos of the foods are in the detail.
                if (photo != null) {
                    FoodPhoto(bytes = photo, size = AapsSpacing.foodThumbnailSize)
                    Spacer(modifier = Modifier.width(AapsSpacing.medium))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = timeText(meal.timestamp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    meal.name?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
                }
                if (foldable) IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(AapsSpacing.compactIconButtonSize)) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(if (expanded) UiStrings.food_book_fold else UiStrings.food_book_unfold)
                    )
                }
                Text(
                    text = stringResource(InterfacesStrings.format_carbs, meal.wizardCarbs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                // The same button as in the treatments history: it shows how the bolus was computed.
                // A carbs entry has no detail: its icon only says what was entered, in the same place.
                val bcr = link?.bcr
                if (bcr != null)
                    CardIcon(type.icon(), stringResource(CoreUiStrings.boluswizard), timeText(meal.timestamp), type.color()) { onShowCalculation(bcr) }
                else
                    Box(modifier = Modifier.size(AapsSpacing.compactIconButtonSize), contentAlignment = Alignment.Center) {
                        Icon(type.icon(), contentDescription = null, modifier = Modifier.size(AapsSpacing.chipIconSize), tint = type.color())
                    }
            }
            if (expanded) meal.items.forEach { item ->
                Text(
                    text = stringResource(
                        UiStrings.food_meal_note_item,
                        item.name,
                        if (item.estimated) stringResource(UiStrings.food_meal_estimated)
                        else stringResource(CoreUiStrings.value_with_unit, MealMath.formatAmount(item.quantity), item.unit),
                        stringResource(InterfacesStrings.format_carbs, item.carbs.roundToInt())
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            when {
                link == null        ->
                    Text(stringResource(UiStrings.food_book_treatment_gone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                link.insulin != null ->
                    Text(
                        text = stringResource(
                            UiStrings.food_book_bolus_link,
                            timeText(link.timestamp),
                            stringResource(InterfacesStrings.format_insulin_units, link.insulin),
                            stringResource(InterfacesStrings.format_carbs, link.carbs.roundToInt())
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                else                 ->
                    Text(
                        text = stringResource(UiStrings.food_book_carbs_link, timeText(link.timestamp), stringResource(InterfacesStrings.format_carbs, link.carbs.roundToInt())),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
            }
            if (link != null && !link.sameNote) {
                Text(stringResource(UiStrings.food_book_bolus_link_weak), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** The lines of an eaten meal with the photo of each food, and the total. */
@Composable
private fun EatenMealDetail(meal: EatenMeal, images: Map<Long, ByteArray>, photos: Map<String, ByteArray>, timeText: (Long) -> String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(meal.name ?: timeText(meal.timestamp)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                if (meal.name != null) Text(timeText(meal.timestamp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                meal.items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                        FoodPhoto(bytes = item.foodId?.let { images[it] } ?: item.photo?.let { photos[it] }, size = AapsSpacing.foodThumbnailSize)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (item.estimated) stringResource(UiStrings.food_meal_estimated)
                                else stringResource(CoreUiStrings.value_with_unit, MealMath.formatAmount(item.quantity), item.unit),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stringResource(CoreUiStrings.value_with_unit, MealMath.formatAmount(item.carbs), stringResource(CoreUiStrings.units_grams)),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider()
                Text(
                    text = stringResource(UiStrings.food_meal_total, stringResource(InterfacesStrings.format_carbs, meal.wizardCarbs)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                NutritionLine(Nutrition.sum(meal.items.map { it.nutrition }))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.close)) } }
    )
}

/** Fat, protein and energy under a total, when one of them is known. */
@Composable
internal fun NutritionLine(nutrition: Nutrition) {
    val text = nutritionText(nutrition)
    if (text.isNotEmpty()) Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EmptyText(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(AapsSpacing.xxLarge), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * One line of the meal or of the dish being changed. A tap changes its quantity, or for a line typed
 * by hand its name and carbs.
 */
@Composable
internal fun MealLine(item: MealItem, image: ByteArray? = null, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        // The photo of the food helps to tell close products apart.
        FoodPhoto(bytes = image, size = AapsSpacing.foodThumbnailSize)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            val amount = stringResource(CoreUiStrings.value_with_unit, MealMath.formatAmount(item.quantity), item.unit)
            Text(
                text = when {
                    item.problem == LineProblem.DELETED      -> stringResource(UiStrings.food_book_line_deleted)
                    item.problem == LineProblem.UNIT_CHANGED -> stringResource(UiStrings.food_book_line_unit_changed, amount)
                    item.estimated                           -> stringResource(UiStrings.food_meal_estimated)
                    else                                     -> amount
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    item.problem != null -> MaterialTheme.colorScheme.error
                    item.estimated       -> MaterialTheme.colorScheme.tertiary
                    else                 -> MaterialTheme.colorScheme.primary
                }
            )
        }
        // Carbs not known: nothing is shown rather than a wrong 0 g.
        if (item.problem == null) Text(
            text = stringResource(CoreUiStrings.value_with_unit, MealMath.formatAmount(item.carbs), stringResource(CoreUiStrings.units_grams)),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(CoreUiStrings.remove))
        }
    }
}

/**
 * Content of the sheet while a dish or a standard meal is changed (pencil). There is no bolus here:
 * this only changes what is saved. The meal being put together waits aside.
 */
@Composable
internal fun RecipeEditContent(
    recipe: Recipe,
    name: String,
    items: List<MealItem>,
    imageOf: (MealItem) -> ByteArray? = { null },
    photo: ByteArray? = null,
    showCamera: Boolean = false,
    onOpenCamera: () -> Unit = {},
    onCloseCamera: () -> Unit = {},
    onPhoto: (ByteArray) -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    onNameChange: (String) -> Unit,
    onEditLine: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onAddFood: () -> Unit,
    onAddByHand: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .consumeOverscroll()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AapsSpacing.xxLarge)
            .padding(bottom = AapsSpacing.xxLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        Text(text = stringResource(UiStrings.food_book_edit_title, recipe.name), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(if (recipe.kind == RecipeKind.DISH) UiStrings.food_book_kind_dish else UiStrings.food_book_kind_meal),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(CoreUiStrings.name)) },
            singleLine = true,
            isError = name.isBlank(),
            modifier = Modifier.fillMaxWidth()
        )
        // A photo of the whole dish or plate: shown on its card and in the history. Optional.
        UserPhotoSection(
            shown = photo,
            canRemove = photo != null,
            showCamera = showCamera,
            onOpenCamera = onOpenCamera,
            onCloseCamera = onCloseCamera,
            onPhoto = onPhoto,
            onRemove = onRemovePhoto,
        )
        Text(text = stringResource(UiStrings.food_book_edit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { item -> MealLine(item, image = imageOf(item), onEdit = { onEditLine(item.key) }, onRemove = { onRemove(item.key) }) }
        HorizontalDivider()
        Text(
            text = stringResource(
                UiStrings.food_meal_total,
                stringResource(CoreUiStrings.value_with_unit, MealMath.formatAmount(MealMath.totalCarbs(items)), stringResource(CoreUiStrings.units_grams))
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        NutritionLine(MealMath.total(items))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onAddFood) { Text(stringResource(UiStrings.food_book_add_food)) }
            TextButton(onClick = onAddByHand) { Text(stringResource(UiStrings.food_meal_add_by_hand)) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(CoreUiStrings.cancel)) }
            TextButton(onClick = onSave, enabled = name.isNotBlank() && items.isNotEmpty()) { Text(stringResource(CoreUiStrings.save)) }
        }
    }
}

/** Asks for the name, and whether the meal is saved as a dish or as a standard meal. */
@Composable
internal fun SaveRecipeDialog(
    state: FoodManagementUiState,
    onNameChange: (String) -> Unit,
    onKindChange: (RecipeKind) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.showSave) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(UiStrings.food_book_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val kinds = listOf(RecipeKind.DISH to UiStrings.food_book_kind_dish, RecipeKind.MEAL to UiStrings.food_book_kind_meal)
                    kinds.forEachIndexed { index, (kind, label) ->
                        SegmentedButton(
                            selected = state.saveKind == kind,
                            onClick = { onKindChange(kind) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size)
                        ) { Text(stringResource(label)) }
                    }
                }
                OutlinedTextField(
                    value = state.saveName,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(CoreUiStrings.name)) },
                    singleLine = true,
                    isError = state.saveName.isBlank()
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = state.saveName.isNotBlank()) { Text(stringResource(CoreUiStrings.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
    )
}

/**
 * Asks before deleting the selected foods, dishes and meals. When a dish or a meal that stays uses
 * one of them, it says so: that total will not be known any more until the dish or meal is changed.
 */
@Composable
internal fun DeleteDialog(summary: DeleteSummary, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CoreUiStrings.confirmation)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                summary.names.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                summary.usedIn.forEach { (name, users) ->
                    Text(
                        text = stringResource(UiStrings.food_book_delete_used, name, joinLines(users)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(CoreUiStrings.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
    )
}

/** Asks which meals go in the CSV export: those of the last days, or all. A tap exports. */
@Composable
internal fun CsvExportDialog(onExport: (days: Int?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(UiStrings.food_csv_period_title)) },
        text = {
            Column {
                CSV_EXPORT_DAYS.forEach { days ->
                    DialogChoice(
                        text = if (days == null) stringResource(UiStrings.food_csv_all) else stringResource(UiStrings.food_csv_last_days, days),
                        onClick = { onExport(days) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
    )
}

/**
 * The backups found in the exports folder, newest first. The text says what a restore does, so a
 * tap on a file restores it without one more question.
 */
@Composable
internal fun RestoreDialog(files: List<ExportFile>, onRestore: (ExportFile) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(UiStrings.food_restore)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                if (files.isEmpty()) Text(stringResource(UiStrings.food_restore_none), style = MaterialTheme.typography.bodyMedium)
                else {
                    Text(stringResource(UiStrings.food_restore_text), style = MaterialTheme.typography.bodyMedium)
                    files.forEach { file -> DialogChoice(text = file.name, onClick = { onRestore(file) }) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
    )
}

@Composable
private fun DialogChoice(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AapsSpacing.large)
    )
}
