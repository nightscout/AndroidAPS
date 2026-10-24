package app.aaps.ui.compose.overview.statusLights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LongOrShort
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.statusLevelToColor
import app.aaps.core.ui.compose.statusLevelToDescription
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull

/**
 * Status rows content — sensor/insulin/cannula/battery with optional action buttons.
 * Does not include a card wrapper — caller provides the container.
 *
 * @see StatusSectionContentPreview
 */
@Composable
internal fun StatusSectionContent(
    sensorStatus: StatusItem?,
    insulinStatus: StatusItem?,
    cannulaStatus: StatusItem?,
    batteryStatus: StatusItem?,
    onSensorInsertClick: (() -> Unit)? = null,
    onFillClick: (() -> Unit)? = null,
    onInsulinChangeClick: (() -> Unit)? = null,
    onBatteryChangeClick: (() -> Unit)? = null
) {
    val addLabel = stringResource(CoreUiStrings.add)
    val cannulaChangeLabel = stringResource(CoreUiStrings.btn_status_cannula_change)
    val insulinFillLabel = stringResource(CoreUiStrings.btn_status_insulin_fill)

    cannulaStatus?.let {
        StatusRow(item = it, actionLabel = cannulaChangeLabel, onActionClick = onFillClick)
    }
    if (cannulaStatus != null && insulinStatus != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    insulinStatus?.let {
        StatusRow(item = it, actionLabel = insulinFillLabel, onActionClick = onInsulinChangeClick)
    }
    if (insulinStatus != null && sensorStatus != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    sensorStatus?.let {
        StatusRow(item = it, actionLabel = addLabel, onActionClick = onSensorInsertClick)
    }
    if (sensorStatus != null && batteryStatus != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    batteryStatus?.let {
        StatusRow(item = it, actionLabel = addLabel, onActionClick = onBatteryChangeClick)
    }
}

@Composable
private fun StatusRow(
    item: StatusItem,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    // The long age where the whole row fits on one line, the short one where it does not. Squeezed by
    // the long age, the label used to break in the middle of the word ("Cann / ula").
    LongOrShort(
        long = { StatusRowContent(item = item, age = item.age, actionLabel = actionLabel, onActionClick = onActionClick) },
        short = { StatusRowContent(item = item, age = item.ageShort, actionLabel = actionLabel, onActionClick = onActionClick) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StatusRowContent(
    item: StatusItem,
    age: String,
    actionLabel: String?,
    onActionClick: (() -> Unit)?
) {
    val ageColor = statusLevelToColor(item.ageStatus)
    val levelColor = statusLevelToColor(item.levelStatus)
    val showLevel = item.level != null && item.expandedLevel

    // This row shows two values at once, so it speaks the worse of the two: naming both severities
    // would be a mouthful, and the same choice is already made on the collapsed Overview chip.
    val worstStatus = when {
        item.ageStatus == StatusLevel.CRITICAL ||
            (showLevel && item.levelStatus == StatusLevel.CRITICAL) -> StatusLevel.CRITICAL

        item.ageStatus == StatusLevel.WARNING ||
            (showLevel && item.levelStatus == StatusLevel.WARNING)  -> StatusLevel.WARNING

        else                                                        -> StatusLevel.NORMAL
    }
    val severity = stringResourceOrNull(statusLevelToDescription(worstStatus))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Everything that is only read is one accessibility node, so a screen reader reads
        // "Critical, Cannula, 5d 12h, 150U" in one stop. It used to stop on the name, then the age,
        // then the level: three swipes for a row nobody can change. The merge has to happen HERE and
        // the value columns must carry no semantics of their own, because a merging parent does not
        // absorb a child that is itself merging - which is also what keeps the action button below
        // separately focusable, so it stays outside this wrapper on purpose.
        //
        // A merge rather than clearAndSetSemantics: clearing would drop the values out of the
        // semantics tree altogether, leaving them unreadable to UI tests and anything else that
        // inspects the tree. Merging keeps each Text and only changes how it is grouped. The
        // description adds the severity, which on a merging node comes before the children instead
        // of replacing them.
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (severity != null) Modifier.semantics(mergeDescendants = true) { contentDescription = severity }
                    else Modifier.semantics(mergeDescendants = true) { }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Icon(
                imageVector = item.icon,
                // Decorative: the Text right beside it already says the same word, so naming the icon
                // too had a screen reader announce "Cannula" twice before reaching the row's value.
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Label. One line even when the short age does not fit either: cut at the end, never
            // broken inside the word.
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Age with vertical progress
            StatusValueWithProgress(
                value = age,
                valueColor = ageColor,
                progress = item.agePercent,
                progressColor = ageColor
            )

            // Level with vertical progress (if available and allowed in expanded view)
            if (item.level != null && showLevel) {
                StatusValueWithProgress(
                    value = item.level,
                    valueColor = levelColor,
                    progress = item.levelPercent,
                    progressColor = levelColor
                )
            }
        }

        // Action button. Name it with the row it belongs to, because the same verb can appear on more
        // than one row - "Add" sits on both Sensor and Battery - while the row's own name is a
        // sibling Text outside the button. Without it a screen reader would hear "Add, button" twice
        // with no way to tell which row it belongs to.
        //
        // Only the row name goes here, not the verb: a Button merges its children and a
        // contentDescription is inserted BEFORE them rather than replacing them, so this reads
        // "Insulin, Fill" and the visible Text still supplies the verb.
        if (actionLabel != null && onActionClick != null) {
            FilledTonalButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(32.dp)
                    .semantics { contentDescription = item.label }
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun StatusValueWithProgress(
    value: String,
    valueColor: Color,
    progress: Float,
    progressColor: Color
) {
    // No semantics of its own: the whole row is one node built by its caller, which already names
    // this value and says how bad it is. The severity used to be announced here, because the colour
    // of [valueColor] was otherwise the only sign that a cannula was overdue or a reservoir nearly
    // empty - that sentence now lives in the row description instead.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
        if (progress >= 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(56.dp)
                    .height(6.dp)
                    // Cleared, or the bar is a focus stop of its own between the row and its button -
                    // "30 percent, progress bar", which says nothing about what it measures while the
                    // row has already said "Cannula, 2d3h". A progress bar carries range information,
                    // which a screen reader treats as a control worth stopping on even inside a merged
                    // parent, so merging the row above is not enough on its own.
                    .clearAndSetSemantics { },
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
        }
    }
}
