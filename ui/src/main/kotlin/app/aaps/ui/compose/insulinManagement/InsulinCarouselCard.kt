package app.aaps.ui.compose.insulinManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.R as CoreUiR

/**
 * Carousel card displaying an insulin configuration.
 * Shows name, peak/DIA summary, concentration badge, and active indicator.
 */
@Composable
fun InsulinCarouselCard(
    iCfg: ICfg,
    isActive: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        isActive   -> AapsTheme.generalColors.inProgress
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        else       -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        isActive   -> AapsTheme.generalColors.onInProgress
        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
        else       -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 8.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Insulin name
            Text(
                text = iCfg.insulinLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Peak / DIA summary
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(CoreUiR.string.format_mins, iCfg.peak),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
                Text(
                    text = stringResource(CoreUiR.string.format_hours, iCfg.dia),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Concentration badge
            val concentration = ConcentrationType.fromDouble(iCfg.concentration)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (concentration != ConcentrationType.U100)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = stringResource(concentration.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (concentration != ConcentrationType.U100)
                        MaterialTheme.colorScheme.onError
                    else
                        MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            // Active indicator
            if (isActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(CoreUiR.string.active).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = AapsTheme.generalColors.activeInsulinText,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
