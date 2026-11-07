package app.aaps.ui.compose.components

import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.overview.graph.BgRange
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/**
 * What a screen reader says for a glucose reading, for example
 * "Glucose: 7.2, high, rising, delta +0.3, 3 min ago, old reading".
 *
 * On screen, high or low is the colour and an old reading is struck through, so both have to be
 * said in words here. Every piece is its own translated string; only the commas join them, which
 * is how a screen reader pauses between items.
 *
 * @param timeAgo already says "ago" in the user's language (`DateUtil.minAgo`), so nothing is added.
 */
@Composable
internal fun bgSpokenDescription(
    bgText: String,
    range: BgRange,
    trend: String?,
    deltaText: String?,
    timeAgo: String?,
    isOutdated: Boolean
): String = buildList {
    add(stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.glucose), bgText))
    when (range) {
        BgRange.HIGH     -> add(stringResource(CoreUiStrings.a11y_high))
        BgRange.LOW      -> add(stringResource(CoreUiStrings.a11y_low))
        BgRange.IN_RANGE -> Unit
    }
    trend?.takeIf { it.isNotBlank() }?.let { add(it) }
    deltaText?.let { add(stringResource(UiStrings.a11y_bg_delta, it)) }
    timeAgo?.takeIf { it.isNotBlank() }?.let { add(it) }
    if (isOutdated) add(stringResource(UiStrings.a11y_bg_old_reading))
}.joinToString(", ")
