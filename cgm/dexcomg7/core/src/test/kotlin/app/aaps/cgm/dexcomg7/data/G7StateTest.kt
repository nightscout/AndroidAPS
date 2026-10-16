package app.aaps.cgm.dexcomg7.data

import app.aaps.cgm.dexcomg7.protocol.G7LifecycleState
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class G7StateTest {

    private val day = 24 * 60 * 60 * 1000L
    private val start = 1_700_000_000_000L

    private val paired = G7State(
        address = "AA:BB:CC:DD:EE:FF",
        pairingCode = "1155",
        sharedKeyHex = "00112233445566778899aabbccddeeff",
        sensorName = "DXCM12",
        activatedAt = start,
        sessionLengthSeconds = 907_200,
        latestAlgorithmState = 6,
        calibration = G7CalibrationRecord(120, start + day),
        alertsIssued = setOf("EXPIRING_SOON")
    )

    @Test
    fun survivesSavingAndLoading() {
        val json = Json { ignoreUnknownKeys = true }
        val text = json.encodeToString(G7State.serializer(), paired)
        assertThat(json.decodeFromString(G7State.serializer(), text)).isEqualTo(paired)
    }

    @Test
    fun oldSavedStateWithUnknownFieldsStillLoads() {
        val json = Json { ignoreUnknownKeys = true }
        val loaded = json.decodeFromString(G7State.serializer(), """{"address":"AA","somethingNew":1}""")
        assertThat(loaded.address).isEqualTo("AA")
    }

    @Test
    fun lifecycleFollowsTheSession() {
        assertThat(G7State().lifecycleState(start)).isEqualTo(G7LifecycleState.UNPAIRED)
        assertThat(paired.copy(activatedAt = null).lifecycleState(start)).isEqualTo(G7LifecycleState.CONNECTING)
        assertThat(paired.lifecycleState(start + day)).isEqualTo(G7LifecycleState.OK)
        assertThat(paired.copy(latestAlgorithmState = 2).lifecycleState(start + 1000)).isEqualTo(G7LifecycleState.WARMUP)
        assertThat(paired.lifecycleState(start + 10 * day + 1000)).isEqualTo(G7LifecycleState.GRACE_PERIOD)
        assertThat(paired.lifecycleState(start + 11 * day)).isEqualTo(G7LifecycleState.EXPIRED)
    }

    @Test
    fun knowsTheModelFromEitherName() {
        assertThat(paired.model?.displayName).isEqualTo("Dexcom G7")
        assertThat(paired.copy(sensorName = "DX0234").model?.displayName).isEqualTo("Dexcom ONE+")
        assertThat(paired.copy(sensorName = null).model).isNull()
    }

    @Test
    fun archivesTheSensor() {
        val record = paired.archived(start + 2 * day, "replaced")
        assertThat(record.sensorName).isEqualTo("DXCM12")
        assertThat(record.activatedAt).isEqualTo(start)
        assertThat(record.endedAt).isEqualTo(start + 2 * day)
        assertThat(record.endReason).isEqualTo("replaced")
    }
}
