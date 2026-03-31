package app.aaps.ui.compose.quickWizard

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.objects.wizard.QuickWizardMode
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.R as CoreR

/**
 * Carousel card displaying a QuickWizard entry.
 * Shows button text, carbs (with eCarbs if enabled), and valid time range.
 *
 * @param entry The QuickWizard entry to display
 * @param isSelected Whether this card is currently selected
 * @param dateUtil For time formatting
 * @param modifier Modifier for the card
 */
@Composable
fun QuickWizardCarouselCard(
    entry: QuickWizardEntry,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val dateUtil = LocalDateUtil.current
    // Card colors
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Icon in top left corner — mode-aware
            val modeIcon = when (entry.mode()) {
                QuickWizardMode.INSULIN -> IcBolus
                QuickWizardMode.CARBS   -> IcCarbs
                QuickWizardMode.WIZARD  -> ElementType.QUICK_WIZARD.icon()
            }
            val modeColor = when (entry.mode()) {
                QuickWizardMode.INSULIN -> ElementType.INSULIN.color()
                QuickWizardMode.CARBS   -> ElementType.CARBS.color()
                QuickWizardMode.WIZARD  -> ElementType.QUICK_WIZARD.color()
            }
            Icon(
                imageVector = modeIcon,
                contentDescription = null,
                tint = modeColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(28.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Button Text (main title)
                Text(
                    text = entry.buttonText().ifEmpty { "?" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Carbs display
                val carbsText = buildCarbsText(entry)
                Text(
                    text = carbsText,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Time range
                val fromTime = dateUtil.timeString(dateUtil.secondsOfTheDayToMillisecondsOfHoursAndMinutes(entry.validFrom()))
                val toTime = dateUtil.timeString(dateUtil.secondsOfTheDayToMillisecondsOfHoursAndMinutes(entry.validTo()))
                Text(
                    text = "$fromTime - $toTime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Build detail text based on mode
 */
private fun buildCarbsText(entry: QuickWizardEntry): String = when (entry.mode()) {
    QuickWizardMode.INSULIN -> "${entry.insulin()} U"
    QuickWizardMode.CARBS, QuickWizardMode.WIZARD -> {
        val carbs = entry.carbs()
        if (entry.useEcarbs() == QuickWizardEntry.YES) {
            val carbs2 = entry.carbs2()
            val duration = entry.duration()
            val time = entry.time()
            "${carbs}g + ${carbs2}g / ${duration}h → ${time}min"
        } else {
            "${carbs}g"
        }
    }
}
