package app.aaps.plugins.automation.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.interfaces.automation.AutomationIconData
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.plugins.automation.actions.Action
import app.aaps.plugins.automation.triggers.Trigger

/**
 * Resolves the theme-aware icon color for an [Action], prioritizing its [ElementType]
 * mapping over fixed legacy tints.
 */
@Composable
fun Action.iconColor(): Color =
    elementType().color()

/**
 * Resolves the theme-aware icon color for a [Trigger], prioritizing its [ElementType]
 * mapping over fixed legacy tints.
 */
@Composable
fun Trigger.iconColor(): Color =
    elementType().color()

/**
 * Resolves the theme-aware icon color for [AutomationIconData], prioritizing its
 * [ElementType] mapping over fixed legacy tints.
 */
@Composable
fun AutomationIconData.iconColor(): Color =
    elementType?.color() ?: MaterialTheme.colorScheme.onSurface
