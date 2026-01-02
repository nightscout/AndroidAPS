package app.aaps.pump.eopatch.vo

import app.aaps.pump.eopatch.code.PatchLifecycle
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class PatchConfigTest {

    @Test
    fun `init should set default values`() {
        val config = PatchConfig()

        assertThat(config.macAddress).isNull()
        assertThat(config.patchSerialNumber).isEmpty()
        assertThat(config.isActivated).isFalse()
        assertThat(config.isDeactivated).isTrue()
        assertThat(config.seq15).isEqualTo(-1)
    }

    @Test
    fun `isActivated should return true when lifecycle is activated`() {
        val config = PatchConfig()
        config.lifecycleEvent = PatchLifecycleEvent().apply {
            lifeCycle = PatchLifecycle.ACTIVATED
        }

        assertThat(config.isActivated).isTrue()
    }

    @Test
    fun `isDeactivated should return true when no mac address`() {
        val config = PatchConfig()
        config.macAddress = null

        assertThat(config.isDeactivated).isTrue()
    }

    @Test
    fun `isDeactivated should return false when mac address exists`() {
        val config = PatchConfig()
        config.macAddress = "00:11:22:33:44:55"

        assertThat(config.isDeactivated).isFalse()
    }

    @Test
    fun `hasMacAddress should return true when mac address is set`() {
        val config = PatchConfig()
        config.macAddress = "00:11:22:33:44:55"

        assertThat(config.hasMacAddress()).isTrue()
    }

    @Test
    fun `hasMacAddress should return false when mac address is null`() {
        val config = PatchConfig()
        config.macAddress = null

        assertThat(config.hasMacAddress()).isFalse()
    }

    @Test
    fun `isExpired should return false when not expired`() {
        val config = PatchConfig()
        config.patchWakeupTimestamp = System.currentTimeMillis()

        assertThat(config.isExpired).isFalse()
    }

    @Test
    fun `isExpired should return true when expired`() {
        val config = PatchConfig()
        // Set wakeup time to 4 days ago
        config.patchWakeupTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4)

        assertThat(config.isExpired).isTrue()
    }

    @Test
    fun `expireTimestamp should be calculated from wakeup time`() {
        val config = PatchConfig()
        val wakeupTime = System.currentTimeMillis()
        config.patchWakeupTimestamp = wakeupTime

        assertThat(config.expireTimestamp).isGreaterThan(wakeupTime)
        assertThat(config.expireTimestamp).isEqualTo(wakeupTime + config.expireDurationMilli)
    }

    @Test
    fun `insulinInjectionAmount should calculate correctly`() {
        val config = PatchConfig()
        config.injectCount = 100 // 100 * 0.05 = 5.0 units

        assertThat(config.insulinInjectionAmount).isWithin(0.001f).of(5.0f)
    }

    @Test
    fun `bolusInjectionAmount should sum standard and extended bolus`() {
        val config = PatchConfig()
        config.standardBolusInjectCount = 20 // 20 * 0.05 = 1.0
        config.extendedBolusInjectCount = 40 // 40 * 0.05 = 2.0

        assertThat(config.bolusInjectionAmount).isWithin(0.001f).of(3.0f)
    }

    @Test
    fun `basalInjectionAmount should calculate correctly`() {
        val config = PatchConfig()
        config.basalInjectCount = 60 // 60 * 0.05 = 3.0

        assertThat(config.basalInjectionAmount).isWithin(0.001f).of(3.0f)
    }

    @Test
    fun `incSeq should increment seq15`() {
        val config = PatchConfig()
        config.seq15 = 0

        config.incSeq()

        assertThat(config.seq15).isEqualTo(1)
    }

    @Test
    fun `incSeq should wrap around at 0x7FFF`() {
        val config = PatchConfig()
        config.seq15 = 0x7FFF

        config.incSeq()

        assertThat(config.seq15).isEqualTo(0)
    }

    @Test
    fun `incSeq should not increment when seq15 is negative`() {
        val config = PatchConfig()
        config.seq15 = -1

        config.incSeq()

        assertThat(config.seq15).isEqualTo(-1)
    }

    @Test
    fun `updateDeactivated should clear all patch data`() {
        val config = PatchConfig()
        config.macAddress = "00:11:22:33:44:55"
        config.patchSerialNumber = "ABC123"
        config.patchFirmwareVersion = "1.0.0"
        config.injectCount = 100
        config.seq15 = 50

        config.updateDeactivated()

        assertThat(config.macAddress).isNull()
        assertThat(config.patchSerialNumber).isEmpty()
        assertThat(config.patchFirmwareVersion).isNull()
        assertThat(config.injectCount).isEqualTo(0)
        assertThat(config.seq15).isEqualTo(-1)
        assertThat(config.lifecycleEvent.lifeCycle).isEqualTo(PatchLifecycle.SHUTDOWN)
    }

    @Test
    fun `updateLifecycle should update lifecycle event`() {
        val config = PatchConfig()
        val event = PatchLifecycleEvent().apply {
            lifeCycle = PatchLifecycle.BONDED
        }

        config.updateLifecycle(event)

        assertThat(config.lifecycleEvent.lifeCycle).isEqualTo(PatchLifecycle.BONDED)
    }

    @Test
    fun `updateLifecycle ACTIVATED should set activation timestamp`() {
        val config = PatchConfig()
        val beforeTime = System.currentTimeMillis()
        val event = PatchLifecycleEvent().apply {
            lifeCycle = PatchLifecycle.ACTIVATED
        }

        config.updateLifecycle(event)
        val afterTime = System.currentTimeMillis()

        assertThat(config.activatedTimestamp).isAtLeast(beforeTime)
        assertThat(config.activatedTimestamp).isAtMost(afterTime)
        assertThat(config.needleInsertionTryCount).isEqualTo(0)
    }

    @Test
    fun `updateLifecycle SHUTDOWN should deactivate patch`() {
        val config = PatchConfig()
        // First set to ACTIVATED state so the transition to SHUTDOWN is meaningful
        config.lifecycleEvent = PatchLifecycleEvent().apply {
            lifeCycle = PatchLifecycle.ACTIVATED
        }
        config.macAddress = "00:11:22:33:44:55"
        config.patchSerialNumber = "ABC123"

        val event = PatchLifecycleEvent().apply {
            lifeCycle = PatchLifecycle.SHUTDOWN
        }

        config.updateLifecycle(event)

        assertThat(config.macAddress).isNull()
        assertThat(config.patchSerialNumber).isEmpty()
    }

    @Test
    fun `updateLifecycle should not update if same lifecycle`() {
        val config = PatchConfig()
        val event1 = PatchLifecycleEvent().apply {
            lifeCycle = PatchLifecycle.BONDED
        }
        config.updateLifecycle(event1)

        val originalActivationTime = config.activatedTimestamp

        // Try to update with same lifecycle
        val event2 = PatchLifecycleEvent().apply {
            lifeCycle = PatchLifecycle.BONDED
        }
        config.updateLifecycle(event2)

        // Should not change
        assertThat(config.activatedTimestamp).isEqualTo(originalActivationTime)
    }

    @Test
    fun `updateNormalBasalPaused should set pause finish time`() {
        val config = PatchConfig()
        val beforeTime = System.currentTimeMillis()

        config.updateNormalBasalPaused(1.0f) // 1 hour pause
        val afterTime = System.currentTimeMillis()

        assertThat(config.basalPauseFinishTimestamp).isAtLeast(beforeTime + TimeUnit.HOURS.toMillis(1))
        assertThat(config.basalPauseFinishTimestamp).isAtMost(afterTime + TimeUnit.HOURS.toMillis(1))
    }

    @Test
    fun `updateNormalBasalResumed should clear pause finish time`() {
        val config = PatchConfig()
        config.basalPauseFinishTimestamp = System.currentTimeMillis() + 10000

        config.updateNormalBasalResumed()

        assertThat(config.basalPauseFinishTimestamp).isEqualTo(0)
    }

    @Test
    fun `isInBasalPausedTime should return true when paused`() {
        val config = PatchConfig()
        config.basalPauseFinishTimestamp = System.currentTimeMillis() + 10000

        assertThat(config.isInBasalPausedTime).isTrue()
    }

    @Test
    fun `isInBasalPausedTime should return false when not paused`() {
        val config = PatchConfig()
        config.basalPauseFinishTimestamp = 0

        assertThat(config.isInBasalPausedTime).isFalse()
    }

    @Test
    fun `isInBasalPausedTime should return false when pause expired`() {
        val config = PatchConfig()
        config.basalPauseFinishTimestamp = System.currentTimeMillis() - 1000

        assertThat(config.isInBasalPausedTime).isFalse()
    }

    @Test
    fun `patchFirmwareVersionString should parse version correctly`() {
        val config = PatchConfig()
        config.patchFirmwareVersion = "1.2.3.456"

        assertThat(config.patchFirmwareVersionString()).isEqualTo("1.2.3")
    }

    @Test
    fun `patchFirmwareVersionString should return full version if less than 3 dots`() {
        val config = PatchConfig()
        config.patchFirmwareVersion = "1.2.3"

        assertThat(config.patchFirmwareVersionString()).isEqualTo("1.2.3")
    }

    @Test
    fun `patchFirmwareVersionString should return null when version is null`() {
        val config = PatchConfig()
        config.patchFirmwareVersion = null

        assertThat(config.patchFirmwareVersionString()).isNull()
    }

    @Test
    fun `updatetDisconnectedTime should set disconnect timestamp`() {
        val config = PatchConfig()
        val beforeTime = System.currentTimeMillis()

        config.updatetDisconnectedTime()
        val afterTime = System.currentTimeMillis()

        assertThat(config.lastDisconnectedTimestamp).isAtLeast(beforeTime)
        assertThat(config.lastDisconnectedTimestamp).isAtMost(afterTime)
    }

    @Test
    fun `update should copy all fields from other config`() {
        val config1 = PatchConfig()
        val config2 = PatchConfig()

        config2.macAddress = "00:11:22:33:44:55"
        config2.patchSerialNumber = "TEST123"
        config2.injectCount = 100
        config2.seq15 = 42

        config1.update(config2)

        assertThat(config1.macAddress).isEqualTo("00:11:22:33:44:55")
        assertThat(config1.patchSerialNumber).isEqualTo("TEST123")
        assertThat(config1.injectCount).isEqualTo(100)
        assertThat(config1.seq15).isEqualTo(42)
    }

    @Test
    fun `equals should return true for identical configs`() {
        val config1 = PatchConfig()
        val config2 = PatchConfig()

        assertThat(config1).isEqualTo(config2)
    }

    @Test
    fun `equals should return false for different configs`() {
        val config1 = PatchConfig()
        val config2 = PatchConfig()
        config2.patchSerialNumber = "DIFFERENT"

        assertThat(config1).isNotEqualTo(config2)
    }

    @Test
    fun `hashCode should be consistent`() {
        val config = PatchConfig()
        val hash1 = config.hashCode()
        val hash2 = config.hashCode()

        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `toString should contain key information`() {
        val config = PatchConfig()
        config.patchSerialNumber = "TEST123"

        val stringRep = config.toString()

        assertThat(stringRep).contains("PatchConfig")
        assertThat(stringRep).contains("patchSerialNumber='TEST123'")
    }
}
