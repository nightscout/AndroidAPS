package app.aaps.pump.carelevo.common

import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.ui.compose.pump.BlePreCheckHost
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** The check every Android pump wizard uses: [BlePreCheck] with its message dialogs. */
@ContributesBinding(AppScope::class)
class AndroidCarelevoBleReadyCheck @Inject constructor(
    private val blePreCheck: BlePreCheck
) : CarelevoBleReadyCheck {

    @Composable
    override fun Check(onReady: () -> Unit, onFailed: () -> Unit) {
        BlePreCheckHost(blePreCheck = blePreCheck, onReady = onReady, onFailed = onFailed)
    }
}
