package app.aaps.core.interfaces.rx.weardata

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

/** Covers [LoopStatusData] + nested [TempTargetInfo]/[TargetRange]/[OapsResultInfo]/LoopMode via JSON round-trip. */
class LoopStatusDataTest {

    private val json = Json

    @Test
    fun fullRoundTrip_preservesAllFields() {
        val data = LoopStatusData(
            timestamp = 1000L,
            loopMode = LoopStatusData.LoopMode.CLOSED,
            apsName = "SMB",
            lastRun = 2000L,
            lastEnact = 3000L,
            tempTarget = TempTargetInfo(targetDisplay = "100 mg/dl", endTime = 5000L, durationMinutes = 30, units = "mg/dl"),
            autosensTarget = "1.0",
            defaultRange = TargetRange(lowDisplay = "80", highDisplay = "120", targetDisplay = "100", units = "mg/dl"),
            oapsResult = OapsResultInfo(
                changeRequested = true, isLetTempRun = false, rate = 1.2, ratePercent = 120,
                duration = 30, reason = "test", smbAmount = 0.5
            )
        )
        val encoded = json.encodeToString(LoopStatusData.serializer(), data)
        val restored = json.decodeFromString(LoopStatusData.serializer(), encoded)
        assertThat(restored).isEqualTo(data)
        assertThat(restored.tempTarget?.durationMinutes).isEqualTo(30)
        assertThat(restored.defaultRange.highDisplay).isEqualTo("120")
        assertThat(restored.oapsResult?.rate).isEqualTo(1.2)
    }

    @Test
    fun roundTrip_withNullableFieldsNull() {
        val data = LoopStatusData(
            timestamp = 0L,
            loopMode = LoopStatusData.LoopMode.DISABLED,
            apsName = null,
            lastRun = null,
            lastEnact = null,
            tempTarget = null,
            defaultRange = TargetRange("70", "180", "110", "mg/dl"),
            oapsResult = null
        )
        val encoded = json.encodeToString(LoopStatusData.serializer(), data)
        assertThat(json.decodeFromString(LoopStatusData.serializer(), encoded)).isEqualTo(data)
    }

    @Test
    fun everyLoopModeRoundTrips() {
        for (mode in LoopStatusData.LoopMode.entries) {
            val d = LoopStatusData(0L, mode, null, null, null, null, null, TargetRange("a", "b", "c", "u"), null)
            val restored = json.decodeFromString(LoopStatusData.serializer(), json.encodeToString(LoopStatusData.serializer(), d))
            assertThat(restored.loopMode).isEqualTo(mode)
        }
    }
}
