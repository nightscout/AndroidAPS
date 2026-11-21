package app.aaps.pump.aaruy.compose

import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import dev.zacsweers.metro.Inject

/** The check every Android pump wizard uses: [BlePreCheck] with its message dialogs. */
@Inject
class AndroidAaruyBleReadyCheck(private val blePreCheck: BlePreCheck) : AaruyBleReadyCheck {

    @Composable
    override fun Check(onReady: () -> Unit, onFailed: () -> Unit) {
        BlePreCheckHost(blePreCheck = blePreCheck, onReady = onReady, onFailed = onFailed)
    }
}
