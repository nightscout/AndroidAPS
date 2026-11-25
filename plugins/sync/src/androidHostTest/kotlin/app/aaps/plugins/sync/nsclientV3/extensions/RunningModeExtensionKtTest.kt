package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.RM
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.nssdk.localmodel.treatment.NSOfflineEvent
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.plugins.sync.extensions.contentEqualsTo
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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

        // Auto-forced DISABLED_LOOP (LoopPlugin.runningModePreCheck writes Long.MAX_VALUE)
        // must normalize to 0 on round-trip — both encode "open-ended permanent" locally,
        // and Long.MAX_VALUE does not round-trip cleanly through JSON's double precision.
        runningMode = RM(
            timestamp = 10000,
            isValid = true,
            mode = RM.Mode.DISABLED_LOOP,
            duration = Long.MAX_VALUE,
            autoForced = true,
            reasons = "constraint denied",
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        runningMode2 = (runningMode.toNSOfflineEvent().convertToRemoteAndBack() as NSOfflineEvent).toRunningMode()
        assertThat(runningMode2.mode).isEqualTo(RM.Mode.DISABLED_LOOP)
        assertThat(runningMode2.duration).isEqualTo(0L)
        assertThat(runningMode2.autoForced).isTrue()
        assertThat(runningMode2.reasons).isEqualTo("constraint denied")
        assertThat(runningMode.ids.contentEqualsTo(runningMode2.ids)).isTrue()
    }

    /**
     * LoopPlugin.runningModePreCheck forces OPEN_LOOP and CLOSED_LOOP_LGS with duration Long.MAX_VALUE too.
     * Sent as it is, Nightscout rounded it to a number too large to read back, and a client or a new install
     * stopped loading treatments there (#5626). They get the open-ended mark 0, as DISABLED_LOOP does.
     */
    @Test
    fun `a forced loop mode goes to Nightscout with the open-ended mark`() {
        for (mode in listOf(RM.Mode.OPEN_LOOP, RM.Mode.CLOSED_LOOP_LGS)) {
            val runningMode = RM(
                timestamp = 10000,
                isValid = true,
                mode = mode,
                duration = Long.MAX_VALUE,
                autoForced = true,
                reasons = "constraint",
                ids = IDs(nightscoutId = "nightscoutId")
            )

            val sent = runningMode.toNSOfflineEvent()
            assertThat(sent.originalDuration).isEqualTo(0L)

            val back = (sent.convertToRemoteAndBack() as NSOfflineEvent).toRunningMode()
            assertThat(back.mode).isEqualTo(mode)
            assertThat(back.duration).isEqualTo(0L)
            assertThat(back.autoForced).isTrue()
            assertThat(back.reasons).isEqualTo("constraint")
        }
    }

    /** A loop mode with a real end keeps it. */
    @Test
    fun `a loop mode with an end keeps its duration`() {
        val runningMode = RM(timestamp = 10000, isValid = true, mode = RM.Mode.CLOSED_LOOP_LGS, duration = 3_600_000L, ids = IDs(nightscoutId = "nightscoutId"))

        val back = (runningMode.toNSOfflineEvent().convertToRemoteAndBack() as NSOfflineEvent).toRunningMode()

        assertThat(back.duration).isEqualTo(3_600_000L)
    }
}
