package app.aaps.pump.dana.compose

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
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.ui.compose.pump.PumpOverviewScreen
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R

@Composable
fun DanaOverviewScreen(
    viewModel: DanaOverviewViewModel,
    danaPump: DanaPump
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val iconRes = when (danaPump.pumpType()) {
        PumpType.DANA_I -> R.drawable.ic_dana_i
        else            -> R.drawable.ic_dana_rs
    }

    PumpOverviewScreen(
        state = uiState,
        customContent = {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp),
                contentScale = ContentScale.Fit
            )
        }
    )
}
