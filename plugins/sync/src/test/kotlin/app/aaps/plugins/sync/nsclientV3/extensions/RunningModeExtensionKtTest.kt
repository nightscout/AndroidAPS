package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.RM
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.nssdk.localmodel.treatment.NSOfflineEvent
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.plugins.sync.extensions.contentEqualsTo
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class RunningModeExtensionKtTest {

    @Test
    fun toOfflineEvent() {
        var runningMode = RM(
            timestamp = 10000,
            isValid = true,
            mode = RM.Mode.DISCONNECTED_PUMP,
            duration = 30000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var runningMode2 = (runningMode.toNSOfflineEvent().convertToRemoteAndBack() as NSOfflineEvent).toRunningMode()
        assertThat(runningMode.contentEqualsTo(runningMode2)).isTrue()
        assertThat(runningMode.ids.contentEqualsTo(runningMode2.ids)).isTrue()

        runningMode = RM(
            timestamp = 10000,
            isValid = true,
            mode = RM.Mode.SUSPENDED_BY_USER,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        runningMode2 = (runningMode.toNSOfflineEvent().convertToRemoteAndBack() as NSOfflineEvent).toRunningMode()
        assertThat(runningMode.contentEqualsTo(runningMode2)).isTrue()
        assertThat(runningMode.ids.contentEqualsTo(runningMode2.ids)).isTrue()

        runningMode = RM(
            timestamp = 10000,
            isValid = true,
            mode = RM.Mode.DISABLED_LOOP,
            duration = 0,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        runningMode2 = (runningMode.toNSOfflineEvent().convertToRemoteAndBack() as NSOfflineEvent).toRunningMode()
        assertThat(runningMode.contentEqualsTo(runningMode2)).isTrue()
        assertThat(runningMode.ids.contentEqualsTo(runningMode2.ids)).isTrue()
    }
}
