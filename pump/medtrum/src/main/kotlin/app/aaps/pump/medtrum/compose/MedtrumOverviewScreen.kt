package app.aaps.pump.medtrum.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.PumpOverviewScreen

@Composable
fun MedtrumOverviewScreen(
    viewModel: MedtrumOverviewViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PumpOverviewScreen(
        state = uiState,
        customContent = {
            Image(
                painter = painterResource(id = app.aaps.core.ui.R.drawable.ic_medtrum_128),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp),
                contentScale = ContentScale.Fit
            )
        }
    )
}
