package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef

/**
 * Screen-reader name for a button that repeats on every row of a list: "Delete: Breakfast".
 *
 * A screen reader moves through such a list one control at a time, so "Delete, Delete, Delete"
 * does not say which item each one deletes. The action and the item name are joined with the
 * shared "label: value" template, so a translator controls the order and punctuation.
 */
@Composable
fun rowAction(action: TextRef, itemName: String): String =
    stringResource(InterfacesStrings.confirmation_line, stringResource(action), itemName)
