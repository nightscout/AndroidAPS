package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.OE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.nssdk.localmodel.treatment.NSOfflineEvent
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.plugins.sync.extensions.contentEqualsTo
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class OfflineEventExtensionKtTest {

    @Test
    fun toOfflineEvent() {
        var offlineEvent = OE(
            timestamp = 10000,
            isValid = true,
            reason = OE.Reason.DISCONNECT_PUMP,
            duration = 30000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var offlineEvent2 = (offlineEvent.toNSOfflineEvent().convertToRemoteAndBack() as NSOfflineEvent).toOfflineEvent()
        assertThat(offlineEvent.contentEqualsTo(offlineEvent2)).isTrue()
        assertThat(offlineEvent.ids.contentEqualsTo(offlineEvent2.ids)).isTrue()

        offlineEvent = OE(
            timestamp = 10000,
            isValid = true,
            reason = OE.Reason.SUSPEND,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        offlineEvent2 = (offlineEvent.toNSOfflineEvent().convertToRemoteAndBack() as NSOfflineEvent).toOfflineEvent()
        assertThat(offlineEvent.contentEqualsTo(offlineEvent2)).isTrue()
        assertThat(offlineEvent.ids.contentEqualsTo(offlineEvent2.ids)).isTrue()

        offlineEvent = OE(
            timestamp = 10000,
            isValid = true,
            reason = OE.Reason.DISABLE_LOOP,
            duration = 0,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        offlineEvent2 = (offlineEvent.toNSOfflineEvent().convertToRemoteAndBack() as NSOfflineEvent).toOfflineEvent()
        assertThat(offlineEvent.contentEqualsTo(offlineEvent2)).isTrue()
        assertThat(offlineEvent.ids.contentEqualsTo(offlineEvent2.ids)).isTrue()
    }
}
