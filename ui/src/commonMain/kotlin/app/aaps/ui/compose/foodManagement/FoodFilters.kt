package app.aaps.ui.compose.foodManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.CoreUiStrings
import app.aaps.ui.UiStrings

/** A filter on a category or a subcategory of a food or a dish. */
sealed interface GroupFilter {

    /** No filter. */
    data object All : GroupFilter

    /** Only the items that have no value, so they can be found and sorted out. */
    data object None : GroupFilter

    data class Named(val name: String) : GroupFilter

    /** True when an item with this category (or subcategory) passes the filter. */
    fun matches(value: String?): Boolean = when (this) {
        All      -> true
        None     -> value.isNullOrBlank()
        is Named -> value == name
    }
}

/**
 * The choices of one filter list, besides "All".
 *
 * @param names the values used, sorted
 * @param hasNone true when "None" is a useful choice: some items have no value, and others have one
 */
data class GroupChoices(val names: List<String> = emptyList(), val hasNone: Boolean = false) {

    companion object {

        fun of(values: List<String?>): GroupChoices {
            val names = values.mapNotNull { it?.trim()?.takeIf { v -> v.isNotEmpty() } }.distinct().sortedBy { nameSortKey(it) }
            return GroupChoices(names, hasNone = names.isNotEmpty() && values.any { it.isNullOrBlank() })
        }
    }
}

private const val ACCENTED = "àáâãäåāăąçćčďèéêëēėęěìíîïīįłñńňòóôõöøōőřśšşťùúûüūůűųýÿźżž"
private const val PLAIN = "aaaaaaaaacccdeeeeeeeeiiiiiilnnnoooooooorssstuuuuuuuuyyzzz"

/**
 * The key to sort names from A to Z: case and accents do not count, so "Écrasé" comes with the "E"
 * and not after "Z". Kept in common code, where no collator is available.
 */
fun nameSortKey(name: String): String = buildString(name.length) {
    name.lowercase().forEach { c ->
        when (c) {
            'æ'  -> append("ae")
            'œ'  -> append("oe")
            'ß'  -> append("ss")
            else -> ACCENTED.indexOf(c).let { i -> append(if (i >= 0) PLAIN[i] else c) }
        }
    }
}

/**
 * The filters at the top of a list: the category on the left, the subcategory on the right, both
 * with a fixed width, so many groups do not take room from the list. A list is not shown when no
 * item uses it. With [sortByName], a button on the right switches between the newest first and A to Z.
 */
@Composable
internal fun GroupFilterRow(
    categories: GroupChoices,
    subCategories: GroupChoices,
    category: GroupFilter,
    subCategory: GroupFilter,
    onCategory: (GroupFilter) -> Unit,
    onSubCategory: (GroupFilter) -> Unit,
    sortByName: Boolean? = null,
    onSortByName: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val showCategory = categories.names.isNotEmpty() || category != GroupFilter.All
    val showSubCategory = showCategory && (subCategories.names.isNotEmpty() || subCategory != GroupFilter.All)
    if (!showCategory && sortByName == null) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.extraLarge),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCategory) GroupFilterField(
            label = stringResource(CoreUiStrings.category),
            choices = categories,
            selected = category,
            onSelect = onCategory,
            modifier = Modifier.weight(1f)
        )
        if (showSubCategory) GroupFilterField(
            label = stringResource(CoreUiStrings.subcategory),
            choices = subCategories,
            selected = subCategory,
            onSelect = onSubCategory,
            modifier = Modifier.weight(1f)
        )
        // The lists keep their width when one of them is not shown.
        else Spacer(Modifier.weight(1f))
        if (sortByName != null) {
            val sortLabel = stringResource(if (sortByName) UiStrings.food_sort_name else UiStrings.food_sort_recent)
            val sortDescription = stringResource(UiStrings.food_sort_description, sortLabel)
            TextButton(
                onClick = { onSortByName(!sortByName) },
                modifier = Modifier.semantics { contentDescription = sortDescription }
            ) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                Spacer(Modifier.width(AapsSpacing.small))
                Text(sortLabel)
            }
        }
    }
}

/** A list to pick "All", one of the values, or "None" (last). It cannot be typed in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupFilterField(label: String, choices: GroupChoices, selected: GroupFilter, onSelect: (GroupFilter) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val all = stringResource(UiStrings.food_filter_all)
    val none = stringResource(UiStrings.food_filter_none)
    val options = buildList {
        add(GroupFilter.All to all)
        choices.names.forEach { add(GroupFilter.Named(it) to it) }
        if (choices.hasNone || selected == GroupFilter.None) add(GroupFilter.None to none)
    }
    val text = when (selected) {
        GroupFilter.All      -> all
        GroupFilter.None     -> none
        is GroupFilter.Named -> selected.name
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (filter, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(filter); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
