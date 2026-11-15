package app.aaps.pump.danars.compose

import androidx.compose.runtime.Composable
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.compose.DanaOverviewScreen

@Composable
fun DanaRSOverviewScreen(
    viewModel: DanaRSOverviewViewModel,
    danaPump: DanaPump
) {
    DanaOverviewScreen(
        viewModel = viewModel,
        danaPump = danaPump
    )
}
