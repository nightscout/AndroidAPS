package app.aaps.pump.eopatch.vo

import app.aaps.pump.eopatch.alarm.AlarmCode
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AlarmsTest {

    private lateinit var alarms: Alarms

    @BeforeEach
    fun setup() {
        alarms = Alarms()
    }

    @Test
    fun `init should have empty alarm collections`() {
        assertThat(alarms.registered).isEmpty()
        assertThat(alarms.occurred).isEmpty()
        assertThat(alarms.needToStopBeep).isEmpty()
    }

    @Test
    fun `register should add alarm to registered map`() {
        val alarmCode = AlarmCode.A003 // Reservoir empty
        val triggerAfter = 5000L

        alarms.register(alarmCode, triggerAfter)

        assertThat(alarms.registered).containsKey(alarmCode)
        val item = alarms.registered[alarmCode]
        assertThat(item).isNotNull()
        assertThat(item?.alarmCode).isEqualTo(alarmCode)
    }

    @Test
    fun `register should replace existing alarm`() {
        val alarmCode = AlarmCode.A003
        alarms.register(alarmCode, 1000L)
        val firstTimestamp = alarms.registered[alarmCode]?.createTimestamp

        Thread.sleep(10) // Ensure different timestamp

        alarms.register(alarmCode, 2000L)
        val secondTimestamp = alarms.registered[alarmCode]?.createTimestamp

        assertThat(alarms.registered).hasSize(1)
        assertThat(secondTimestamp).isNotEqualTo(firstTimestamp)
    }

    @Test
    fun `unregister should remove alarm from registered map`() {
        val alarmCode = AlarmCode.A003
        alarms.register(alarmCode, 5000L)

        alarms.unregister(alarmCode)

        assertThat(alarms.registered).doesNotContainKey(alarmCode)
    }

    @Test
    fun `unregister should not fail for non-existent alarm`() {
        val alarmCode = AlarmCode.A003

        // Should not throw exception
        alarms.unregister(alarmCode)

        assertThat(alarms.registered).isEmpty()
    }

    @Test
    fun `occurred should move alarm from registered to occurred`() {
        val alarmCode = AlarmCode.A003
        alarms.register(alarmCode, 5000L)

        alarms.occurred(alarmCode)

        assertThat(alarms.registered).doesNotContainKey(alarmCode)
        assertThat(alarms.occurred).containsKey(alarmCode)
    }

    @Test
    fun `occurred should not add if alarm not registered`() {
        val alarmCode = AlarmCode.A003

        alarms.occurred(alarmCode)

        assertThat(alarms.occurred).doesNotContainKey(alarmCode)
    }

    @Test
    fun `occurred should not duplicate if already occurring`() {
        val alarmCode = AlarmCode.A003
        alarms.register(alarmCode, 5000L)
        alarms.occurred(alarmCode)

        alarms.occurred(alarmCode) // Try to occur again

        assertThat(alarms.occurred).hasSize(1)
    }

    @Test
    fun `isOccurring should return true for occurred alarm`() {
        val alarmCode = AlarmCode.A003
        alarms.register(alarmCode, 5000L)
        alarms.occurred(alarmCode)

        assertThat(alarms.isOccurring(alarmCode)).isTrue()
    }

    @Test
    fun `isOccurring should return false for non-occurred alarm`() {
        val alarmCode = AlarmCode.A003

        assertThat(alarms.isOccurring(alarmCode)).isFalse()
    }

    @Test
    fun `handle should remove alarm from occurred`() {
        val alarmCode = AlarmCode.A003
        alarms.register(alarmCode, 5000L)
        alarms.occurred(alarmCode)

        alarms.handle(alarmCode)

        assertThat(alarms.occurred).doesNotContainKey(alarmCode)
        assertThat(alarms.isOccurring(alarmCode)).isFalse()
    }

    @Test
    fun `handle should not fail for non-occurred alarm`() {
        val alarmCode = AlarmCode.A003

        // Should not throw exception
        alarms.handle(alarmCode)

        assertThat(alarms.occurred).isEmpty()
    }

    @Test
    fun `getOccuredAlarmTimestamp should return trigger time for occurred alarm`() {
        val alarmCode = AlarmCode.A003
        alarms.register(alarmCode, 5000L)
        alarms.occurred(alarmCode)

        val timestamp = alarms.getOccuredAlarmTimestamp(alarmCode)

        assertThat(timestamp).isGreaterThan(0)
    }

    @Test
    fun `getOccuredAlarmTimestamp should return current time for non-occurred alarm`() {
        val alarmCode = AlarmCode.A003
        val beforeTime = System.currentTimeMillis()

        val timestamp = alarms.getOccuredAlarmTimestamp(alarmCode)

        assertThat(timestamp).isAtLeast(beforeTime)
    }

    @Test
    fun `clear should remove all alarms`() {
        alarms.register(AlarmCode.A003, 5000L)
        alarms.register(AlarmCode.A016, 3000L) // Patch expiration
        alarms.occurred(AlarmCode.A003)
        alarms.needToStopBeep.add(AlarmCode.A016)

        alarms.clear()

        assertThat(alarms.registered).isEmpty()
        assertThat(alarms.occurred).isEmpty()
        assertThat(alarms.needToStopBeep).isEmpty()
    }

    @Test
    fun `update should copy alarms from other instance`() {
        val other = Alarms()
        other.register(AlarmCode.A003, 5000L)
        other.occurred(AlarmCode.A003)

        alarms.update(other)

        assertThat(alarms.registered).isEqualTo(other.registered)
        assertThat(alarms.occurred).isEqualTo(other.occurred)
    }

    @Test
    fun `AlarmItem should store alarm details`() {
        val item = Alarms.AlarmItem()
        item.alarmCode = AlarmCode.A003
        item.createTimestamp = 1000L
        item.triggerTimeMilli = 6000L

        assertThat(item.alarmCode).isEqualTo(AlarmCode.A003)
        assertThat(item.createTimestamp).isEqualTo(1000L)
        assertThat(item.triggerTimeMilli).isEqualTo(6000L)
    }

    @Test
    fun `AlarmItem toString should contain alarm details`() {
        val item = Alarms.AlarmItem()
        item.alarmCode = AlarmCode.A003
        item.createTimestamp = 1000L
        item.triggerTimeMilli = 6000L

        val str = item.toString()

        assertThat(str).contains("AlarmItem")
        assertThat(str).contains("alarmCode=")
        assertThat(str).contains("createTimestamp=1000")
        assertThat(str).contains("triggerTimeMilli=6000")
    }

    @Test
    fun `multiple alarms can be registered simultaneously`() {
        alarms.register(AlarmCode.A003, 5000L)
        alarms.register(AlarmCode.A016, 3000L)
        alarms.register(AlarmCode.A020, 7000L) // Occlusion

        assertThat(alarms.registered).hasSize(3)
        assertThat(alarms.registered).containsKey(AlarmCode.A003)
        assertThat(alarms.registered).containsKey(AlarmCode.A016)
        assertThat(alarms.registered).containsKey(AlarmCode.A020)
    }

    @Test
    fun `alarm lifecycle should work correctly`() {
        val alarmCode = AlarmCode.A003

        // Register
        alarms.register(alarmCode, 5000L)
        assertThat(alarms.registered).containsKey(alarmCode)
        assertThat(alarms.occurred).doesNotContainKey(alarmCode)

        // Occur
        alarms.occurred(alarmCode)
        assertThat(alarms.registered).doesNotContainKey(alarmCode)
        assertThat(alarms.occurred).containsKey(alarmCode)
        assertThat(alarms.isOccurring(alarmCode)).isTrue()

        // Handle
        alarms.handle(alarmCode)
        assertThat(alarms.occurred).doesNotContainKey(alarmCode)
        assertThat(alarms.isOccurring(alarmCode)).isFalse()
    }

    @Test
    fun `toString should contain alarm state`() {
        alarms.register(AlarmCode.A003, 5000L)
        alarms.occurred(AlarmCode.A003)

        val str = alarms.toString()

        assertThat(str).contains("Alarms")
        assertThat(str).contains("registered=")
        assertThat(str).contains("occurred=")
    }

    @Test
    fun `observe should return observable`() {
        val observable = alarms.observe()

        assertThat(observable).isNotNull()
    }

    @Test
    fun `triggerTimeMilli should be calculated correctly`() {
        val triggerAfter = 10000L
        val beforeRegister = System.currentTimeMillis()

        alarms.register(AlarmCode.A003, triggerAfter)

        val item = alarms.registered[AlarmCode.A003]
        assertThat(item?.createTimestamp).isAtLeast(beforeRegister)
        assertThat(item?.triggerTimeMilli).isEqualTo(item?.createTimestamp?.plus(triggerAfter))
    }
}
