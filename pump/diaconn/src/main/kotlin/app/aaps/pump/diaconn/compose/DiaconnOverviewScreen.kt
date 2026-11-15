package app.aaps.pump.diaconn.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.icons.IcDiaconn
import app.aaps.core.ui.compose.pump.PumpOverviewScreen

@Composable
fun DiaconnOverviewScreen(
    viewModel: DiaconnOverviewViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PumpOverviewScreen(
        state = uiState,
        customContent = {
            Image(
                imageVector = IcDiaconn,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
    )
}
