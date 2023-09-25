package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.treatment.NSOfflineEvent
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class OfflineEventExtensionKtTest {

    @Test
    fun toOfflineEvent() {
        var offlineEvent = OfflineEvent(
            timestamp = 10000,
            isValid = true,
            reason = OfflineEvent.Reason.DISCONNECT_PUMP,
            duration = 30000,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        var offlineEvent2 = (offlineEvent.toNSOfflineEvent().convertToRemoteAndBack() as NSOfflineEvent).toOfflineEvent()
        assertThat(offlineEvent.contentEqualsTo(offlineEvent2)).isTrue()
        assertThat(offlineEvent.interfaceIdsEqualsTo(offlineEvent2)).isTrue()

        offlineEvent = OfflineEvent(
            timestamp = 10000,
            isValid = true,
            reason = OfflineEvent.Reason.SUSPEND,
            duration = 3600000,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        offlineEvent2 = (offlineEvent.toNSOfflineEvent().convertToRemoteAndBack() as NSOfflineEvent).toOfflineEvent()
        assertThat(offlineEvent.contentEqualsTo(offlineEvent2)).isTrue()
        assertThat(offlineEvent.interfaceIdsEqualsTo(offlineEvent2)).isTrue()

        offlineEvent = OfflineEvent(
            timestamp = 10000,
            isValid = true,
            reason = OfflineEvent.Reason.DISABLE_LOOP,
            duration = 0,
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        offlineEvent2 = (offlineEvent.toNSOfflineEvent().convertToRemoteAndBack() as NSOfflineEvent).toOfflineEvent()
        assertThat(offlineEvent.contentEqualsTo(offlineEvent2)).isTrue()
        assertThat(offlineEvent.interfaceIdsEqualsTo(offlineEvent2)).isTrue()
    }
}
