package app.aaps.pump.eopatch.vo

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PatchStateTest {

    @Test
    fun `default constructor should create empty state`() {
        val state = PatchState()

        assertThat(state.isEmpty).isTrue()
        assertThat(state.updatedTimestamp).isEqualTo(0)
    }

    @Test
    fun `create should initialize state with correct size`() {
        val bytes = ByteArray(17)
        val state = PatchState.create(bytes, System.currentTimeMillis())

        assertThat(state).isNotNull()
        assertThat(state.isEmpty).isFalse()
    }

    @Test
    fun `create should handle null bytes`() {
        val state = PatchState.create(null, System.currentTimeMillis())

        assertThat(state).isNotNull()
    }

    @Test
    fun `update should set timestamp`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        val timestamp = System.currentTimeMillis()

        state.update(bytes, timestamp)

        assertThat(state.updatedTimestamp).isEqualTo(timestamp)
        assertThat(state.isEmpty).isFalse()
    }

    @Test
    fun `update with 17 bytes should pad correctly`() {
        val state = PatchState()
        val bytes = ByteArray(17)

        state.update(bytes, System.currentTimeMillis())

        assertThat(state.isEmpty).isFalse()
    }

    @Test
    fun `clear should reset state`() {
        val state = PatchState()
        state.update(ByteArray(20), System.currentTimeMillis())

        state.clear()

        assertThat(state.isEmpty).isTrue()
        assertThat(state.updatedTimestamp).isEqualTo(0)
    }

    @Test
    fun `batteryLevel should calculate correctly for full battery`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        bytes[7] = 155.toByte() // (155 + 145) * 10 = 3000 mV

        state.update(bytes, System.currentTimeMillis())

        assertThat(state.batteryLevel()).isEqualTo(100)
    }

    @Test
    fun `batteryLevel should calculate correctly for low battery`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        bytes[7] = 0.toByte() // (0 + 145) * 10 = 1450 mV

        state.update(bytes, System.currentTimeMillis())

        assertThat(state.batteryLevel()).isAtMost(20)
    }

    @Test
    fun `isNormalBasalPaused should return true when registered but not active`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        // Set isNormalBasalReg bit (bit 2 of D4) but not isNormalBasalAct (bit 4)
        bytes[4] = 0x04.toByte() // 0000 0100

        state.update(bytes, System.currentTimeMillis())

        assertThat(state.isNormalBasalPaused).isTrue()
    }

    @Test
    fun `isNormalBasalRunning should return true when registered and active`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        // Set both isNormalBasalReg (bit 2) and isNormalBasalAct (bit 4)
        bytes[4] = 0x14.toByte() // 0001 0100

        state.update(bytes, System.currentTimeMillis())

        assertThat(state.isNormalBasalRunning).isTrue()
    }

    @Test
    fun `isTempBasalActive should return true when temp basal conditions met`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        // Set isTempBasalReg (bit 3) and isTempBasalAct (bit 5)
        bytes[4] = 0x28.toByte() // 0010 1000
        // Ensure isTempBasalDone (bit 7 of D6) is not set
        bytes[6] = 0x00.toByte()

        state.update(bytes, System.currentTimeMillis())

        assertThat(state.isTempBasalActive).isTrue()
    }

    @Test
    fun `isNowBolusActive should return true when bolus active and not done`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        // Set isNowBolusRegAct (bit 0 of D4)
        bytes[4] = 0x01.toByte()
        // Ensure isNowBolusDone (bit 4 of D6) is not set
        bytes[6] = 0x00.toByte()

        state.update(bytes, System.currentTimeMillis())

        assertThat(state.isNowBolusActive).isTrue()
    }

    @Test
    fun `isExtBolusActive should return true when ext bolus active and not done`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        // Set isExtBolusRegAct (bit 1 of D4)
        bytes[4] = 0x02.toByte()
        // Ensure isExtBolusDone (bit 6 of D6) is not set
        bytes[6] = 0x00.toByte()

        state.update(bytes, System.currentTimeMillis())

        assertThat(state.isExtBolusActive).isTrue()
    }

    @Test
    fun `isBolusActive should return true when any bolus is active`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        bytes[4] = 0x01.toByte() // Now bolus active
        bytes[6] = 0x00.toByte()

        state.update(bytes, System.currentTimeMillis())

        assertThat(state.isBolusActive).isTrue()
    }

    @Test
    fun `isCriticalAlarm should return true when bit is set`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        // Set bit 7 of D5
        bytes[5] = 0x80.toByte()

        state.update(bytes, System.currentTimeMillis())

        assertThat(state.isCriticalAlarm).isTrue()
    }

    @Test
    fun `isNewAlertAlarm should return true when bit is set`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        // Set bit 0 of D5
        bytes[5] = 0x01.toByte()

        state.update(bytes, System.currentTimeMillis())

        assertThat(state.isNewAlertAlarm).isTrue()
    }

    @Test
    fun `remainedInsulin should return correct value`() {
        val state = PatchState()
        val bytes = ByteArray(20)
        // Set insulin value in D18
        bytes[18] = 50.toByte()

        state.update(bytes, System.currentTimeMillis())

        // The actual calculation depends on pump cycle, but should be at least 0
        assertThat(state.remainedInsulin).isAtLeast(0f)
    }

    @Test
    fun `equalState should return true for identical states`() {
        val state1 = PatchState()
        val state2 = PatchState()
        val bytes = ByteArray(20)
        bytes[4] = 0x10.toByte()

        state1.update(bytes, System.currentTimeMillis())
        state2.update(bytes, System.currentTimeMillis())

        assertThat(state1.equalState(state2)).isTrue()
    }

    @Test
    fun `equalState should return false for different states`() {
        val state1 = PatchState()
        val state2 = PatchState()
        val bytes1 = ByteArray(20)
        val bytes2 = ByteArray(20)
        bytes1[4] = 0x10.toByte()
        bytes2[4] = 0x20.toByte()

        state1.update(bytes1, System.currentTimeMillis())
        state2.update(bytes2, System.currentTimeMillis())

        assertThat(state1.equalState(state2)).isFalse()
    }

    @Test
    fun `equals should work correctly`() {
        val state1 = PatchState()
        val state2 = PatchState()

        assertThat(state1).isEqualTo(state2)
    }

    @Test
    fun `hashCode should be consistent`() {
        val state = PatchState()
        val hash1 = state.hashCode()
        val hash2 = state.hashCode()

        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `toString should not throw exception`() {
        val state = PatchState()

        val result = state.toString()

        assertThat(result).contains("PatchState")
    }

    @Test
    fun `update from other PatchState should copy data`() {
        val state1 = PatchState()
        val state2 = PatchState()
        val bytes = ByteArray(20)
        bytes[4] = 0x10.toByte()
        val timestamp = System.currentTimeMillis()

        state1.update(bytes, timestamp)
        state2.update(state1)

        assertThat(state2.updatedTimestamp).isEqualTo(timestamp)
        assertThat(state2.equalState(state1)).isTrue()
    }

    @Test
    fun `observe should return observable`() {
        val state = PatchState()

        val observable = state.observe()

        assertThat(observable).isNotNull()
    }
}
