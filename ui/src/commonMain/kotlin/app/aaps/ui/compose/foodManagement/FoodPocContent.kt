package app.aaps.ui.compose.foodManagement

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import app.aaps.core.data.model.FD
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.foodManagement.book.decodeFoodImage
import app.aaps.ui.compose.foodManagement.openfoodfacts.BarcodeScanner
import app.aaps.ui.compose.foodManagement.openfoodfacts.OffProduct
import app.aaps.ui.compose.foodManagement.openfoodfacts.OffResult
import app.aaps.ui.compose.foodManagement.openfoodfacts.OpenFoodFactsRepository
import app.aaps.ui.compose.foodManagement.openfoodfacts.barcodeScannerAvailable
import kotlin.math.roundToInt

// PROOF OF CONCEPT: the Open Food Facts import and the meal. Kept apart from the food screen so the
// experiment is easy to see, review and throw away.

/** Content of the import bottom sheet: search field, optional camera, results. */
@Composable
internal fun OffImportContent(
    state: FoodManagementUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenScanner: () -> Unit,
    onCloseScanner: () -> Unit,
    onScanned: (String) -> Unit,
    onSelect: (OffProduct) -> Unit,
    onMakeFood: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .consumeOverscroll()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AapsSpacing.xxLarge)
            .padding(bottom = AapsSpacing.xxLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
    ) {
        Text(text = stringResource(UiStrings.food_off_import), style = MaterialTheme.typography.titleLarge)
        OffNotice(text = stringResource(UiStrings.food_off_warning_short), details = stringResource(UiStrings.food_off_warning))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.importQuery,
                onValueChange = onQueryChange,
                label = { Text(stringResource(UiStrings.food_off_search_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                // A search is sent on the key or the button only: OFF allows about 10 per minute.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )
            IconButton(onClick = onSearch, enabled = !state.importInProgress && state.importQuery.isNotBlank()) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(CoreUiStrings.search))
            }
            if (barcodeScannerAvailable) {
                IconButton(onClick = if (state.showScanner) onCloseScanner else onOpenScanner) {
                    Icon(
                        imageVector = if (state.showScanner) Icons.Filled.Close else Icons.Filled.QrCodeScanner,
                        contentDescription = stringResource(UiStrings.food_off_scan)
                    )
                }
            }
        }

        if (state.showScanner) {
            BarcodeScanner(
                onScanned = onScanned,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
            )
        }

        when (val result = state.importResult) {
            null                   -> if (state.importInProgress) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            OffResult.NotFound     -> {
                Text(stringResource(UiStrings.food_off_not_found))
                // A barcode OFF does not know is kept on a food typed by hand.
                if (OpenFoodFactsRepository.isBarcode(state.importQuery.trim()))
                    TextButton(onClick = onMakeFood) { Text(stringResource(UiStrings.food_barcode_make_food)) }
            }

            OffResult.Offline      -> Text(stringResource(UiStrings.food_off_offline), color = MaterialTheme.colorScheme.error)
            OffResult.RateLimited  -> Text(stringResource(UiStrings.food_off_rate_limited), color = MaterialTheme.colorScheme.error)
            OffResult.Failed       -> Text(stringResource(UiStrings.food_off_failed), color = MaterialTheme.colorScheme.error)
            is OffResult.Found     -> result.products.forEach { product ->
                OffProductCard(
                    product = product,
                    image = state.importImages[product.barcode],
                    inList = state.foodsWithBarcode(product.barcode).firstOrNull()?.name,
                    onClick = { onSelect(product) }
                )
            }
        }

        Text(
            text = stringResource(UiStrings.food_off_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OffProductCard(product: OffProduct, image: ByteArray?, inList: String?, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // The photo of the package is often what tells two close products apart.
        Row(modifier = Modifier.padding(AapsSpacing.large), horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
            FoodPhoto(bytes = image, size = AapsSpacing.foodThumbnailSize)
            OffProductTexts(product = product, inList = inList, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OffProductTexts(product: OffProduct, inList: String?, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = product.name, style = MaterialTheme.typography.titleSmall)
        product.brand?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val unit = if (product.isLiquid) "ml" else "g"
        val carbs = product.carbsPer100?.let { grams(it) } ?: stringResource(UiStrings.food_off_unknown)
        Text(
            text = stringResource(InterfacesStrings.confirmation_line, stringResource(InterfacesStrings.carbs), stringResource(UiStrings.food_off_carbs_per_100, carbs, unit)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (product.carbsPer100 == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        product.servingSize?.let {
            Text(text = stringResource(UiStrings.food_off_serving, it), style = MaterialTheme.typography.bodySmall)
        }
        Text(text = product.barcode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The product is already in the list: a second food for it is rarely wanted.
        inList?.let {
            Text(text = stringResource(UiStrings.food_barcode_in_list, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/**
 * The barcode just scanned or typed is already in the list. A tap on a food opens it. A second food
 * can still be made: one for 100 g and one for 1 pot of the same product, for example.
 */
@Composable
internal fun BarcodeOwnersDialog(owners: List<FD>, onOpen: (FD) -> Unit, onMakeAnother: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(UiStrings.food_barcode_known_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                Text(stringResource(UiStrings.food_barcode_known_text), style = MaterialTheme.typography.bodyMedium)
                owners.forEach { food ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(food) }
                            .padding(vertical = AapsSpacing.medium)
                    ) {
                        Text(text = food.name, style = MaterialTheme.typography.titleSmall)
                        Text(text = groupOf(food), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = stringResource(CoreUiStrings.value_with_unit, MealMath.formatAmount(food.portion), food.unit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onMakeAnother) { Text(stringResource(UiStrings.food_barcode_make_another)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
    )
}

/** "Breakfast / Cereals", or only the category, or "No category". */
@Composable
private fun groupOf(food: FD): String {
    val category = food.category?.takeIf { it.isNotBlank() } ?: return stringResource(UiStrings.food_barcode_no_category)
    val subCategory = food.subCategory?.takeIf { it.isNotBlank() } ?: return category
    return stringResource(UiStrings.food_barcode_group, category, subCategory)
}

/**
 * The product photo of a food, or nothing when there is none (a food typed by hand, a product
 * without a photo, or a photo still downloading). The name is always written next to it, so the
 * photo has no text for screen readers.
 */
@Composable
internal fun FoodPhoto(bytes: ByteArray?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) { bytes?.let { decodeFoodImage(it) } } ?: return
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
    )
}

/**
 * Shown at the top of the food editor when it was filled from OFF: the choice between the OFF serving
 * and 100 g, the exact OFF values, and a short reminder to check them.
 */
@Composable
internal fun OffEditorBanner(product: OffProduct, perServing: Boolean, onPerServingChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        val baseUnit = if (product.isLiquid) "ml" else "g"
        val serving = MealMath.servingPortion(product)
        if (serving != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                FilterChip(
                    selected = perServing,
                    onClick = { onPerServingChange(true) },
                    label = { Text(product.servingSize ?: stringResource(CoreUiStrings.value_with_unit, MealMath.formatAmount(serving.portion), serving.unit)) }
                )
                FilterChip(
                    selected = !perServing,
                    onClick = { onPerServingChange(false) },
                    label = { Text(stringResource(CoreUiStrings.value_with_unit, "100", baseUnit)) }
                )
            }
        }
        Text(stringResource(UiStrings.food_off_values_follow), style = MaterialTheme.typography.bodySmall)
        val unknown = stringResource(UiStrings.food_off_unknown)
        Text(
            text = stringResource(
                UiStrings.food_off_exact_values,
                baseUnit,
                product.carbsPer100?.let { grams(it) } ?: unknown,
                product.sugarsPer100?.let { grams(it) } ?: unknown,
                product.fiberPer100?.let { grams(it) } ?: unknown
            ),
            style = MaterialTheme.typography.bodySmall
        )
        if (product.carbsPer100 == null) {
            Text(stringResource(UiStrings.food_off_no_carbs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        // The values are as sold. When OFF also has the prepared product (dry pasta, a soup powder),
        // the user is told: counting cooked pasta with the values of dry pasta gives about twice the carbs.
        product.carbsPreparedPer100?.let { prepared ->
            Text(
                text = stringResource(UiStrings.food_off_prepared_too, grams(prepared), baseUnit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        OffNotice(text = stringResource(UiStrings.food_off_warning_short), details = stringResource(UiStrings.food_off_warning))
    }
}

/**
 * A one-line reminder. With [details], a tap shows the full text. Kept small on purpose: the user
 * sees it at each step, so a large red card soon gets ignored.
 */
@Composable
private fun OffNotice(text: String, details: String? = null) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (details != null) Modifier.clickable { expanded = !expanded } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(
            text = if (expanded && details != null) details else text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A weight with one decimal, like "4.7 g". */
@Composable
private fun grams(value: Double): String =
    stringResource(CoreUiStrings.value_with_unit, MealMath.formatAmount(value), stringResource(CoreUiStrings.units_grams))

/**
 * The bar under the lists while a meal is being put together, or while a dish or a meal is changed
 * ([editingName]). A tap opens the sheet again.
 */
@Composable
internal fun MealBar(items: List<MealItem>, editingName: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (editingName != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        val carbs = stringResource(InterfacesStrings.format_carbs, MealMath.wizardCarbs(items))
        Text(
            text = if (editingName != null) stringResource(UiStrings.food_book_edit_bar, editingName, carbs)
            else stringResource(UiStrings.food_meal_bar, items.size, carbs),
            style = MaterialTheme.typography.titleMedium,
            color = if (editingName != null) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(AapsSpacing.extraLarge)
        )
    }
}

/**
 * Content of the meal bottom sheet: the foods, the total, and the way to the carbs dialog or the
 * wizard. A tap on a line changes its quantity.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MealContent(
    items: List<MealItem>,
    sourceName: String?,
    imageOf: (MealItem) -> ByteArray? = { null },
    onEditLine: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
    onAddFood: () -> Unit,
    onAddByHand: () -> Unit,
    onSave: () -> Unit,
    onCarbs: () -> Unit,
    onCalculate: () -> Unit,
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
        Text(text = sourceName ?: stringResource(UiStrings.food_meal_title), style = MaterialTheme.typography.titleLarge)
        items.forEach { item -> MealLine(item, image = imageOf(item), onEdit = { onEditLine(item.key) }, onRemove = { onRemove(item.key) }) }
        HorizontalDivider()
        Text(
            text = stringResource(UiStrings.food_meal_total, grams(MealMath.totalCarbs(items))),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        NutritionLine(MealMath.total(items))
        OffNotice(text = stringResource(UiStrings.food_meal_check))
        // Wraps to two lines on a narrow screen instead of squeezing the buttons.
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onAddFood) { Text(stringResource(UiStrings.food_book_add_food)) }
            TextButton(onClick = onAddByHand) { Text(stringResource(UiStrings.food_meal_add_by_hand)) }
            TextButton(onClick = onSave) { Text(stringResource(CoreUiStrings.save)) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClear) { Text(stringResource(CoreUiStrings.clear)) }
            Row {
                TreatmentButton(ElementType.CARBS, stringResource(InterfacesStrings.carbs), onCarbs)
                TreatmentButton(ElementType.BOLUS_WIZARD, stringResource(CoreUiStrings.boluswizard), onCalculate)
            }
        }
    }
}

/**
 * Adds or changes a line typed by hand: a name and the carbs the user estimated, for something that
 * is not in the food list (a home-made quiche). The line is marked as estimated everywhere.
 */
@Composable
internal fun HandLineDialog(
    state: FoodManagementUiState,
    onNameChange: (String) -> Unit,
    onCarbsChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.showHandLine) return
    val carbs = MealMath.parseAmount(state.handLineCarbs)
    val valid = carbs != null && state.handLineName.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(UiStrings.food_meal_by_hand_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                OutlinedTextField(
                    value = state.handLineName,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(CoreUiStrings.name)) },
                    singleLine = true,
                    isError = state.handLineName.isBlank(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = state.handLineCarbs,
                    onValueChange = onCarbsChange,
                    label = { Text(stringResource(CoreUiStrings.label_carbs_g)) },
                    singleLine = true,
                    isError = carbs == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm() })
                )
                OffNotice(text = stringResource(UiStrings.food_meal_by_hand_hint))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = valid) {
                Text(stringResource(if (state.handLineKey != null) CoreUiStrings.ok else UiStrings.food_meal_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
    )
}

/** A text button with the icon and the colour this action has everywhere in the app. */
@Composable
private fun TreatmentButton(type: ElementType, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(type.icon(), contentDescription = null, tint = type.color(), modifier = Modifier.size(AapsSpacing.chipIconSize))
        Spacer(Modifier.width(AapsSpacing.small))
        Text(label, color = type.color())
    }
}

/**
 * Asks how much of one food, or how many servings of one dish, go into the meal. Opened from a line
 * of the meal ([FoodManagementUiState.quantityLineKey]), it changes that line instead.
 */
@Composable
internal fun QuantityDialog(
    state: FoodManagementUiState,
    carbsFor: (Double) -> Double?,
    onQuantityChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val food = state.quantityFood
    val recipe = state.quantityRecipe
    val name = food?.name ?: recipe?.name ?: return
    val unit = when {
        food == null     -> stringResource(UiStrings.food_book_servings)
        food.portion > 0 -> food.unit
        else             -> stringResource(UiStrings.food_meal_portions)
    }
    val quantity = MealMath.parseAmount(state.quantityText)
    val carbs = quantity?.let(carbsFor)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(UiStrings.food_meal_quantity_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                OutlinedTextField(
                    value = state.quantityText,
                    onValueChange = onQuantityChange,
                    label = { Text(stringResource(UiStrings.food_meal_quantity, unit)) },
                    singleLine = true,
                    isError = quantity == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (quantity != null) onConfirm() })
                )
                if (carbs != null) {
                    Text(
                        text = stringResource(InterfacesStrings.format_carbs, carbs.roundToInt()),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = carbs != null) {
                Text(stringResource(if (state.quantityLineKey != null) CoreUiStrings.ok else UiStrings.food_meal_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreUiStrings.cancel)) } }
    )
}
