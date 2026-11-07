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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import app.aaps.core.ui.compose.AapsSpacing
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * Long help text, folded away until asked for.
 *
 * The start screen carried five permanently expanded help blocks — about two screens of scrolling
 * before the first control. They are worth reading once and in the way once the user knows the
 * flow, so they keep their place but start closed. [tone] lets a warning stay recognisable while
 * closed, because an unread warning that looks like ordinary help is not a warning.
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
    val titleColor = when (tone) {
        CgmCardTone.Neutral -> MaterialTheme.colorScheme.onSurface
        CgmCardTone.Warning -> MaterialTheme.colorScheme.error
    }
    CgmCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = titleColor,
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

/**
 * The guided-flow progress of the start screen, in the form the window can afford.
 *
 * Wide: the four labelled steps side by side. Compact: "Step 2 of 4" plus the step name and a
 * four-segment bar — no label is ever squeezed into 82 dp, so nothing truncates on a narrow phone
 * or when the theme scales type up on a tablet.
 */
@Composable
fun CgmStepper(
    currentStep: Int,
    labels: List<String>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(
                        SourceStrings.cgm_step_counter,
                        (currentStep + 1).coerceAtMost(labels.size),
                        labels.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = labels.getOrElse(currentStep) { labels.last() },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                labels.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(AapsSpacing.chipProgressHeight)
                            .background(
                                color = if (index <= currentStep) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(AapsSpacing.extraSmall),
                            ),
                    )
                }
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            labels.forEachIndexed { index, label ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = AapsSpacing.large)
                            .height(AapsSpacing.extraSmall)
                            .background(
                                if (index <= currentStep) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.small),
                    modifier = Modifier.width(AapsSpacing.xxLarge * 3),
                ) {
                    Box(
                        modifier = Modifier
                            .size(AapsSpacing.chipIconSize)
                            .background(
                                color = if (index <= currentStep) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (index <= currentStep) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = if (index <= currentStep) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
