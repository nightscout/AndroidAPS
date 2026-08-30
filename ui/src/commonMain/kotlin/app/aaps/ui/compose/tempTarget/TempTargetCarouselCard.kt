package app.aaps.ui.compose.tempTarget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalProfileUtil
import app.aaps.core.ui.compose.formatMinutesAsDuration
import app.aaps.core.ui.compose.icons.IcTtActivity
import app.aaps.core.ui.compose.icons.IcTtEatingSoon
import app.aaps.core.ui.compose.icons.IcTtHypo
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import kotlinx.coroutines.delay

/**
 * Carousel card displaying a temp target preset or active TT.
 * Shows name, target value, duration, and TT reason badge.
 * For active TT, shows remaining time progress indicator.
 *
 * @param preset The preset to display (null for active TT card)
 * @param activeTT The currently active temp target (null if none)
 * @param remainingTimeMs Remaining time in milliseconds for active TT
 * @param isSelected Whether this card is currently selected
 * @param units Current glucose units (mg/dL or mmol/L)
 * @param modifier Modifier for the card
 */
@Composable
fun TempTargetCarouselCard(
    preset: TTPreset?,
    activeTT: TT?,
    remainingTimeMs: Long?,
    isSelected: Boolean,
    units: GlucoseUnit,
    onExpired: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    val profileUtil = LocalProfileUtil.current
    val isActiveCard = activeTT != null
    val reason = preset?.reason ?: activeTT?.reason ?: TT.Reason.CUSTOM

    // Auto-update progress for active TT
    var progress by remember(isActiveCard, activeTT?.timestamp, activeTT?.duration) {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(isActiveCard, activeTT?.timestamp, activeTT?.duration) {
        if (isActiveCard) {
            while (true) {
                val now = dateUtil.now()
                val elapsed = now - activeTT.timestamp
                val remaining = activeTT.duration - elapsed
                if (remaining > 0) {
                    progress = (elapsed.toFloat() / activeTT.duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    progress = 1f
                    onExpired()
                    return@LaunchedEffect
                }
                delay(30_000L) // Update every 30 seconds
            }
        } else {
            progress = 0f
        }
    }

    // Card colors - active TT uses inProgress (yellow) from theme
    val containerColor = when {
        isActiveCard -> AapsTheme.generalColors.inProgress
        isSelected   -> MaterialTheme.colorScheme.secondaryContainer
        else         -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        isActiveCard -> AapsTheme.generalColors.onInProgress
        isSelected   -> MaterialTheme.colorScheme.onSecondaryContainer
        else         -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    // TT reason badge color
    val badgeColor = getTTReasonColor(reason)

    // Get icon drawable resource based on reason
    val icon = getTTReasonIcon(reason)

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActiveCard) 8.dp else 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // TT icon in top left corner (matches original dialog icons)
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(32.dp),
                tint = badgeColor
            )

            // Padding and spacing are deliberately tighter than the 16/8 defaults: name + target +
            // duration + badge needs a few dp more than a 200dp card leaves, and a Column measures
            // its children in order, so the shortfall lands entirely on the last one — the badge was
            // being clamped to half height and its text cut through the middle.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Preset/TT name - for standalone active card show TT reason as name
                val nameText = when {
                    preset != null -> preset.displayName ?: preset.name ?: ""
                    isActiveCard   -> stringResource(getTTReasonStringRes(reason))
                    else           -> ""
                }
                Text(
                    text = nameText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Target value (handle range vs single target)
                val targetValueMgdl = preset?.targetValue ?: activeTT?.lowTarget ?: 100.0
                val targetText = profileUtil.fromMgdlToStringInUnits(targetValueMgdl)
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Duration (formatted as "Xh Ym" when >= 60 minutes)
                val durationMs = preset?.duration ?: activeTT?.duration ?: 0L
                val durationMinutes = (durationMs / 60000L).toInt()
                Text(
                    text = formatMinutesAsDuration(durationMinutes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                // TT Reason badge
                val reasonResId = getTTReasonStringRes(reason)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = stringResource(reasonResId),
                        style = MaterialTheme.typography.labelMedium,
                        color = AapsTheme.generalColors.onBadge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                // Active indicator
                if (isActiveCard) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(CoreUiStrings.active).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = AapsTheme.generalColors.activeInsulinText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Progress indicator for active TT
            if (isActiveCard && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = AapsTheme.generalColors.activeInsulinText,
                    trackColor = contentColor.copy(alpha = 0.2f)
                )
            }
        }
    }
}

/**
 * Get color for TT reason badge based on reason type
 */
@Composable
private fun getTTReasonColor(reason: TT.Reason): Color {
    val colors = AapsTheme.generalColors
    return when (reason) {
        TT.Reason.EATING_SOON  -> colors.ttEatingSoon
        TT.Reason.ACTIVITY     -> colors.ttActivity
        TT.Reason.HYPOGLYCEMIA -> colors.ttHypoglycemia
        TT.Reason.CUSTOM,
        TT.Reason.AUTOMATION,
        TT.Reason.WEAR         -> colors.ttCustom
    }
}

/**
 * Get string resource ID for TT reason
 */
private fun getTTReasonStringRes(reason: TT.Reason): TextRef {
    return when (reason) {
        TT.Reason.EATING_SOON  -> CoreUiStrings.eatingsoon
        TT.Reason.ACTIVITY     -> CoreUiStrings.activity
        TT.Reason.HYPOGLYCEMIA -> CoreUiStrings.hypo
        TT.Reason.CUSTOM       -> CoreUiStrings.custom
        TT.Reason.AUTOMATION   -> CoreUiStrings.automation
        TT.Reason.WEAR         -> CoreUiStrings.wear
    }
}

/**
 * Get compose ImageVector for TT reason icon
 */
private fun getTTReasonIcon(reason: TT.Reason): ImageVector {
    return when (reason) {
        TT.Reason.EATING_SOON  -> IcTtEatingSoon
        TT.Reason.ACTIVITY     -> IcTtActivity
        TT.Reason.HYPOGLYCEMIA -> IcTtHypo
        TT.Reason.CUSTOM,
        TT.Reason.AUTOMATION,
        TT.Reason.WEAR         -> IcTtManual
    }
}
