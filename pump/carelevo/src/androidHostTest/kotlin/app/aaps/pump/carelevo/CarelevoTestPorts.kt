package app.aaps.pump.carelevo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.pump.carelevo.common.CarelevoAppForeground
import app.aaps.pump.carelevo.common.CarelevoBleReadyCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** AAPS in front or not, as the test sets it in [state]. */
class FakeCarelevoAppForeground(inFront: Boolean = false) : CarelevoAppForeground {

    val state = MutableStateFlow(inFront)

    override fun isInForeground(): Boolean = state.value

    override fun inForeground(): Flow<Boolean> = state
}

/** Says Bluetooth is ready at once, or never, as the test sets it. */
class FakeCarelevoBleReadyCheck(private val ready: Boolean = true) : CarelevoBleReadyCheck {

    var checks = 0

    @Composable
    override fun Check(onReady: () -> Unit, onFailed: () -> Unit) {
        LaunchedEffect(Unit) {
            checks++
            if (ready) onReady()
        }
    }
}

/** A central that only reports the adapter state the test keeps in [adapterState]. */
fun bleCentralWith(adapterState: MutableStateFlow<AdapterState>): BleCentral =
    mock<BleCentral>().also { whenever(it.adapterState).thenReturn(adapterState) }
