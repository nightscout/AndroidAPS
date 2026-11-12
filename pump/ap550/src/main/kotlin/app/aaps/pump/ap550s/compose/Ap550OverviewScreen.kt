package app.aaps.pump.ap550s.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.pump.ap550s.R

/** Size of the connection icon in the compact card. */
private val CONNECTION_ICON_SIZE = 36.dp

/** One full turn of the connecting icon, same speed as the old fragment. */
private const val CONNECTING_ROTATION_MS = 1200

/**
 * Overview screen for the AP550 driver.
 *
 * Like on the old screen, the connection card sits at the very top, above the shared
 * [PumpOverviewScreen] (status banner, info rows and buttons). The icon and the status text are
 * centred as one group, and the card is the tap target for connect and disconnect.
 */
@Composable
fun Ap550OverviewScreen(
    viewModel: Ap550OverviewViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connection by viewModel.connectionState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Ap550ConnectionCard(
            connection = connection,
            onCardClick = viewModel::onConnectionIconClick,
            modifier = Modifier.padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium)
        )

        PumpOverviewScreen(
            state = uiState,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Connection card: the pump icon of the old screen and the status text beside it, centred as one
 * group.
 *
 * While the pump is connecting, the icon keeps turning, like the old fragment did with its
 * rotation animator.
 */
@Composable
private fun Ap550ConnectionCard(
    connection: Ap550ConnectionUiState,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconRotation = remember { Animatable(0f) }
    LaunchedEffect(connection.phase) {
        if (connection.phase == Ap550ConnectionPhase.CONNECTING) {
            while (true) {
                iconRotation.animateTo(
                    targetValue = iconRotation.value + 360f,
                    animationSpec = tween(durationMillis = CONNECTING_ROTATION_MS, easing = LinearEasing)
                )
            }
        } else {
            iconRotation.snapTo(0f)
        }
    }
    AapsCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCardClick)
                .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large, Alignment.CenterHorizontally)
        ) {
            Image(
                painter = painterResource(connectionDrawable(connection.phase)),
                contentDescription = connection.statusText,
                modifier = Modifier
                    .size(CONNECTION_ICON_SIZE)
                    .graphicsLayer { rotationZ = iconRotation.value }
            )
            Column {
                Text(text = connection.statusText, style = MaterialTheme.typography.titleMedium)
                if (connection.description.isNotEmpty()) {
                    Text(
                        text = connection.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun connectionDrawable(phase: Ap550ConnectionPhase): Int = when (phase) {
    Ap550ConnectionPhase.UNBOUND               -> R.drawable.guide_change_device
    Ap550ConnectionPhase.CONNECTING            -> R.drawable.ic_pump_connecting_48dp
    Ap550ConnectionPhase.CONNECTED_NOT_PAIRED  -> R.drawable.ic_pump_disconnected_48dp
    Ap550ConnectionPhase.CONNECTED             -> R.drawable.ic_pump_connected_48dp
    Ap550ConnectionPhase.DISCONNECTING         -> R.drawable.ic_pump_disconnecting_48dp
    Ap550ConnectionPhase.DISCONNECTED          -> R.drawable.ic_pump_disconnected_48dp
}