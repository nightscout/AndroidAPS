package app.aaps.core.ui.compose.preference

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import kotlinx.coroutines.delay

/**
 * Helper function to add preference content inline in a LazyListScope.
 * Handles PreferenceSubScreenDef only.
 */
fun LazyListScope.addPreferenceContent(
    content: Any,
    sectionState: PreferenceSectionState? = null
) {
    when (content) {
        is PreferenceSubScreenDef -> addPreferenceSubScreenDef(content, sectionState)
    }
}

/**
 * Helper function to add PreferenceSubScreenDef inline in a LazyListScope.
 * This displays as one collapsible card with main content and nested subscreens inside.
 * Content is rendered using the new pattern (no NavigablePreferenceContent interface).
 */
fun LazyListScope.addPreferenceSubScreenDef(
    def: PreferenceSubScreenDef,
    sectionState: PreferenceSectionState? = null
) {
    val sectionKey = "${def.key}_main"
    item(key = sectionKey) {
        val isExpanded = sectionState?.isExpanded(sectionKey) ?: false
        // Get visibility context from CompositionLocal
        val visibilityContext = LocalVisibilityContext.current
        CollapsibleCardSectionContent(
            titleResId = def.titleResId,
            summaryItems = def.effectiveSummaryItems(),
            expanded = isExpanded,
            onToggle = { sectionState?.toggle(sectionKey, SectionLevel.TOP_LEVEL) },
            iconResId = def.iconResId,
            icon = def.icon
        ) {
            // Render items in order, preserving the original structure
            RenderPreferenceItems(
                items = def.items,
                parentKey = def.key,
                sectionState = sectionState,
                visibilityContext = visibilityContext
            )
        }
    }
}

/**
 * Helper composable to render a list of preference items with visibility support.
 */
@Composable
private fun RenderPreferenceItems(
    items: List<Any>,
    parentKey: String,
    sectionState: PreferenceSectionState?,
    visibilityContext: PreferenceVisibilityContext?
) {
    items.forEach { item ->
        when (item) {
            is PreferenceKey          -> {
                HighlightablePreference(preferenceKey = item.key) {
                    AdaptivePreferenceItem(
                        key = item,
                        visibilityContext = visibilityContext
                    )
                }
            }

            is PreferenceSubScreenDef -> {
                val shouldShow = shouldShowSubScreenInline(
                    subScreen = item,
                    visibilityContext = visibilityContext
                )

                if (shouldShow) {
                    // Render nested subscreen as simple collapsible section (no extra card)
                    val subSectionKey = "${parentKey}_${item.key}"
                    val isSubExpanded = sectionState?.isExpanded(subSectionKey) ?: false

                    // Header without card (no icon for nested subscreens)
                    ClickablePreferenceCategoryHeader(
                        titleResId = item.titleResId,
                        summaryItems = item.effectiveSummaryItems(),
                        expanded = isSubExpanded,
                        onToggle = { sectionState?.toggle(subSectionKey, SectionLevel.SUB_SECTION, parentKey = parentKey) },
                        insideCard = true,
                        iconResId = null  // No icon for nested subscreens
                    )

                    // Content without card wrapper
                    if (isSubExpanded) {
                        if (item.items.isNotEmpty()) {
                            val theme = LocalPreferenceTheme.current
                            Column(
                                modifier = Modifier.padding(start = theme.nestedContentIndent)
                            ) {
                                AdaptivePreferenceList(
                                    items = item.items,
                                    visibilityContext = visibilityContext,
                                    onNavigateToSubScreen = null // Nested subscreens not supported here
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Determines if a subscreen should be shown based on hideParentScreenIfHidden logic.
 * Used in inline rendering context (AllPreferencesScreen).
 */
@Composable
private fun shouldShowSubScreenInline(
    subScreen: PreferenceSubScreenDef,
    visibilityContext: PreferenceVisibilityContext?
): Boolean {
    // Find items with hideParentScreenIfHidden = true
    for (item in subScreen.items) {
        if (item is PreferenceKey && item.hideParentScreenIfHidden) {
            val visibility = if (item is IntentPreferenceKey) {
                // Check visibility of intent item
                calculateIntentPreferenceVisibility(
                    intentKey = item,
                    visibilityContext = visibilityContext
                )
            } else {
                // Get engineeringModeOnly based on specific type
                val engineeringModeOnly = when (item) {
                    is BooleanPreferenceKey -> item.engineeringModeOnly
                    is IntPreferenceKey     -> item.engineeringModeOnly
                    is LongPreferenceKey    -> item.engineeringModeOnly
                    else                    -> false
                }
                // Check visibility of regular preference item
                calculatePreferenceVisibility(
                    preferenceKey = item,
                    engineeringModeOnly = engineeringModeOnly,
                    visibilityContext = visibilityContext
                )
            }
            // If this controlling item is hidden, hide the parent subscreen
            if (!visibility.visible) {
                return false
            }
        }
    }
    // No hideParentScreenIfHidden items found, or all are visible
    return true
}

/**
 * Wrapper that highlights a preference if it matches the LocalHighlightKey.
 * Shows a brief color flash animation to draw attention to the preference.
 */
@Composable
private fun HighlightablePreference(
    preferenceKey: String,
    content: @Composable () -> Unit
) {
    val highlightKey = LocalHighlightKey.current
    val shouldHighlight = highlightKey == preferenceKey

    var isHighlighted by remember { mutableStateOf(shouldHighlight) }

    // Animate highlight fade out
    LaunchedEffect(shouldHighlight) {
        if (shouldHighlight) {
            isHighlighted = true
            delay(2000) // Keep highlight for 2 seconds
            isHighlighted = false
        }
    }

    val highlightColor by animateColorAsState(
        targetValue = if (isHighlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 500),
        label = "highlightColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(highlightColor)
    ) {
        content()
    }
}
