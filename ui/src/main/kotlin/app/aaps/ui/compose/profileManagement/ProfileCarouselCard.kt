package app.aaps.ui.compose.profileManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.EPS
import app.aaps.core.data.time.T
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.ui.R
import kotlinx.coroutines.delay

/**
 * Card displayed in the carousel for a single profile.
 */
@Composable
internal fun ProfileCarouselCard(
    profileName: String,
    basalSum: Double,
    isActive: Boolean,
    hasErrors: Boolean,
    activeProfileSwitch: EPS?,
    nextProfileName: String?,
    formatBasalSum: (Double) -> String,
    modifier: Modifier = Modifier
) {
    // Check if active profile has modifications (percentage, timeshift, or duration)
    val isModified = activeProfileSwitch?.let { eps ->
        eps.originalPercentage != 100 || eps.originalTimeshift != 0L || eps.originalDuration != 0L
    } ?: false

    // Calculate progress for temporary profiles with auto-update
    val hasDuration = activeProfileSwitch?.originalDuration?.let { it > 0L } ?: false
    var progress by remember { mutableFloatStateOf(0f) }

    // Auto-update progress every 30 seconds
    LaunchedEffect(isActive, hasDuration, activeProfileSwitch?.timestamp) {
        if (isActive && hasDuration) {
            while (true) {
                val now = System.currentTimeMillis()
                val elapsed = now - activeProfileSwitch.timestamp
                val remaining = activeProfileSwitch.originalDuration - elapsed
                progress = if (remaining > 0) {
                    (elapsed.toFloat() / activeProfileSwitch.originalDuration.toFloat()).coerceIn(0f, 1f)
                } else 1f
                delay(30_000L) // Update every 30 seconds
            }
        } else {
            progress = 0f
        }
    }

    val containerColor = when {
        hasErrors              -> MaterialTheme.colorScheme.errorContainer
        isActive && isModified -> AapsTheme.generalColors.inProgress
        isActive               -> MaterialTheme.colorScheme.primaryContainer
        else                   -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        hasErrors              -> MaterialTheme.colorScheme.onErrorContainer
        isActive && isModified -> AapsTheme.generalColors.onInProgress
        isActive               -> MaterialTheme.colorScheme.onPrimaryContainer
        else                   -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 8.dp else 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Profile icon in top left corner
            Icon(
                imageVector = ElementType.PROFILE_MANAGEMENT.icon(),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(24.dp),
                tint = contentColor.copy(alpha = 0.6f)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Profile name
                Text(
                    text = profileName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Basal sum
                Text(
                    text = "∑ ${formatBasalSum(basalSum)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )

                // Error indicator
                if (hasErrors) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(app.aaps.core.ui.R.string.invalid),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Active indicator and percentage/timeshift
                if (isActive && !hasErrors) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Show "ACTIVE -> Next: profile" when there's a next profile, otherwise just "ACTIVE"
                    val activeText = if (hasDuration && nextProfileName != null) {
                        "${stringResource(R.string.active_profile_indicator)} → $nextProfileName"
                    } else {
                        stringResource(R.string.active_profile_indicator)
                    }
                    Text(
                        text = activeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isModified) contentColor else AapsTheme.generalColors.activeInsulinText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Percentage and timeshift for active profile
                    activeProfileSwitch?.let { eps ->
                        val details = buildString {
                            if (eps.originalPercentage != 100) {
                                append("${eps.originalPercentage}%")
                            }
                            val timeshiftHours = T.msecs(eps.originalTimeshift).hours().toInt()
                            if (timeshiftHours != 0) {
                                if (isNotEmpty()) append(" ")
                                append(if (timeshiftHours > 0) "+${timeshiftHours}h" else "${timeshiftHours}h")
                            }
                        }
                        if (details.isNotEmpty()) {
                            Text(
                                text = details,
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor
                            )
                        }
                    }
                }
            }

            // Progress indicator for temporary profiles at bottom
            if (isActive && hasDuration && progress < 1f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(4.dp),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.3f)
                )
            }
        }
    }
}
