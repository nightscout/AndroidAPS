package app.aaps.pump.aaruy.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.icons.IcAaruy
import app.aaps.core.ui.compose.icons.IcDiaconn
import app.aaps.core.ui.compose.pump.PumpOverviewScreen

@Composable
fun AaruyOverviewScreen(
    viewModel: AaruyOverviewViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PumpOverviewScreen(
        state = uiState,
        customContent = {
            Image(
                imageVector = IcAaruy,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp),
                contentScale = ContentScale.Fit
            )
        }
    )
}