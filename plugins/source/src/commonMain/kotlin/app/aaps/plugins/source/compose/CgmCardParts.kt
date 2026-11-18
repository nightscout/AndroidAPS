package app.aaps.plugins.source.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.statusLevelToColor
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.source.SourceStrings

/**
 * A block of related information, with its own surface.
 *
 * The original screens were a flat run of `Text` lines, so "which sensor does this line describe?"
 * had no visual answer once there were two sensors on screen. A card gives each subject a boundary.
 *
 * @param accent true for the card that describes the sensor currently feeding the loop, so it can
 *   be told apart from the pre-soak sensor without reading either heading.
 */
@Composable
fun CgmCard(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    tone: CgmCardTone = CgmCardTone.Neutral,
    content: @Composable ColumnScope.() -> Unit,
) {
    val container = when {
        tone == CgmCardTone.Warning -> MaterialTheme.colorScheme.errorContainer
        accent                          -> MaterialTheme.colorScheme.secondaryContainer
        else                            -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier.padding(AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            content = content,
        )
    }
}

/** Whether a card carries ordinary information or something the user has to act on. */
enum class CgmCardTone { Neutral, Warning }

/**
 * Card heading: an uppercase label on the left, room for a state chip on the right.
 */
@Composable
fun CgmCardHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * One fact: its name on the left, its value on the right.
 *
 * The value is aligned right so a column of them lines up, which is what makes a card of readings
 * scannable rather than a paragraph.
 */
@Composable
fun CgmKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label keeps its own width and the value takes the rest. The other way round, a long
        // value left the label a few characters wide and broke it in the middle of a word.
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Long help text, folded away until asked for.
 *
 * The start screen carried five permanently expanded help blocks — about two screens of scrolling
 * before the first control. They are worth reading once and in the way once the user knows the
 * flow, so they keep their place but start closed. [tone] lets a warning stay recognisable while
 * closed (a warning sign before the title), because an unread warning that looks like ordinary help
 * is not a warning.
 */
@Composable
fun CgmHelpCard(
    title: String,
    modifier: Modifier = Modifier,
    tone: CgmCardTone = CgmCardTone.Neutral,
    expandedByDefault: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(expandedByDefault) }
    CgmCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A warning is marked with a sign, not with red text: the sign is also read out by a
            // screen reader, and the title stays as easy to read as the others.
            if (tone == CgmCardTone.Warning) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = stringResource(CoreUiStrings.warning),
                    tint = statusLevelToColor(StatusLevel.WARNING),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) SourceStrings.cgm_help_collapse else SourceStrings.cgm_help_expand,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                content = content,
            )
        }
    }
}

/**
 * How far along a sequence of named steps something is — nodes on a line, with their labels.
 *
 * A state label alone ("Settling") says where a thing is but not how far through it is, nor what
 * comes next. The nodes say both without a sentence.
 *
 * @param reached index of the current step; -1 when the sequence has not started.
 */
@Composable
fun CgmStepTimeline(
    labels: List<String>,
    reached: Int,
    modifier: Modifier = Modifier,
) {
    val done = MaterialTheme.colorScheme.primary
    val todo = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            labels.indices.forEach { index ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(AapsSpacing.extraSmall)
                            .background(if (index <= reached) done else todo),
                    )
                }
                TimelineNode(reached = index <= reached, current = index == reached, done = done, todo = todo)
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index <= reached) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = when (index) {
                        0                -> TextAlign.Start
                        labels.lastIndex -> TextAlign.End
                        else             -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TimelineNode(
    reached: Boolean,
    current: Boolean,
    done: Color,
    todo: Color,
) {
    Box(
        modifier = Modifier.size(AapsSpacing.extraLarge),
        contentAlignment = Alignment.Center,
    ) {
        if (current) {
            Box(
                modifier = Modifier
                    .size(AapsSpacing.extraLarge)
                    .background(color = done.copy(alpha = 0.25f), shape = CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .size(AapsSpacing.large)
                .background(color = if (reached) done else todo, shape = CircleShape),
        )
    }
}

