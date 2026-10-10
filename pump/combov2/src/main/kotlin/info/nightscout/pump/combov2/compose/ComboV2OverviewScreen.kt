package info.nightscout.pump.combov2.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import info.nightscout.pump.combov2.ComboV2RTDisplayFrameView
import info.nightscout.pump.combov2.R

@Composable
fun ComboV2OverviewScreen(
    viewModel: ComboV2OverviewViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val displayFrame by viewModel.displayFrame.collectAsStateWithLifecycle()

    PumpOverviewScreen(
        state = state.overview,
        customContent = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.isPaired) {
                    if (state.currentActivityText.isNotEmpty()) {
                        AapsCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = state.currentActivityText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                LinearProgressIndicator(
                                    progress = { state.currentActivityProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                )
                            }
                        }
                    }

                    AapsCard(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Combo RT display is 96x32 pixels, rendered at 2x
                            AndroidView(
                                modifier = Modifier.size(width = 192.dp, height = 64.dp),
                                factory = { ctx -> ComboV2RTDisplayFrameView(ctx) },
                                update = { view -> view.displayFrame = displayFrame }
                            )
                        }
                    }
                }

                Image(
                    painter = painterResource(R.drawable.ic_combov2),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                )
            }
        }
    )
}
