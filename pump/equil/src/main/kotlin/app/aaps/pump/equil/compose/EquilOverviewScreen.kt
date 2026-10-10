package app.aaps.pump.equil.compose

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
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.pump.PumpOverviewScreen

private val PUMP_IMAGE_HEIGHT = 128.dp

@Composable
internal fun EquilOverviewScreen(
    viewModel: EquilOverviewViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PumpOverviewScreen(
        state = uiState,
        customContent = {
            Image(
                painter = painterResource(id = R.drawable.ic_equil_128),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PUMP_IMAGE_HEIGHT),
                contentScale = ContentScale.Fit
            )
        }
    )
}
