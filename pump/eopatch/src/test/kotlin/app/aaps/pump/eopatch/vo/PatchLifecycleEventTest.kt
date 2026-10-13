package app.aaps.pump.eopatch.vo

import app.aaps.pump.eopatch.code.PatchLifecycle
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PatchLifecycleEventTest {

    @Test
    fun `default constructor should create shutdown event`() {
        val event = PatchLifecycleEvent()

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.SHUTDOWN)
        assertThat(event.timestamp).isGreaterThan(0)
        assertThat(event.isShutdown).isTrue()
    }

    @Test
    fun `constructor with lifecycle should set lifecycle correctly`() {
        val event = PatchLifecycleEvent(PatchLifecycle.ACTIVATED)

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.ACTIVATED)
        assertThat(event.timestamp).isGreaterThan(0)
        assertThat(event.isActivated).isTrue()
    }

    @Test
    fun `create should create event with specified lifecycle`() {
        val event = PatchLifecycleEvent.create(PatchLifecycle.BONDED)

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.BONDED)
    }

    @Test
    fun `createShutdown should create shutdown event`() {
        val event = PatchLifecycleEvent.createShutdown()

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.SHUTDOWN)
        assertThat(event.isShutdown).isTrue()
    }

    @Test
    fun `createBonded should create bonded event`() {
        val event = PatchLifecycleEvent.createBonded()

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.BONDED)
    }

    @Test
    fun `createRemoveNeedleCap should create remove needle cap event`() {
        val event = PatchLifecycleEvent.createRemoveNeedleCap()

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.REMOVE_NEEDLE_CAP)
    }

    @Test
    fun `createRemoveProtectionTape should create remove protection tape event`() {
        val event = PatchLifecycleEvent.createRemoveProtectionTape()

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.REMOVE_PROTECTION_TAPE)
    }

    @Test
    fun `createSafetyCheck should create safety check event`() {
        val event = PatchLifecycleEvent.createSafetyCheck()

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.SAFETY_CHECK)
        assertThat(event.isSafetyCheck).isTrue()
    }

    @Test
    fun `createRotateKnob should create rotate knob event`() {
        val event = PatchLifecycleEvent.createRotateKnob()

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.ROTATE_KNOB)
        assertThat(event.isRotateKnob).isTrue()
    }

    @Test
    fun `createBasalSetting should create basal setting event`() {
        val event = PatchLifecycleEvent.createBasalSetting()

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.BASAL_SETTING)
        assertThat(event.isBasalSetting).isTrue()
    }

    @Test
    fun `createActivated should create activated event`() {
        val event = PatchLifecycleEvent.createActivated()

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.ACTIVATED)
        assertThat(event.isActivated).isTrue()
    }

    @Test
    fun `isSubStepRunning should be true for intermediate steps`() {
        // Sub-steps are between SHUTDOWN and ACTIVATED
        assertThat(PatchLifecycleEvent.createBonded().isSubStepRunning).isTrue()
        assertThat(PatchLifecycleEvent.createSafetyCheck().isSubStepRunning).isTrue()
        assertThat(PatchLifecycleEvent.createRemoveNeedleCap().isSubStepRunning).isTrue()
        assertThat(PatchLifecycleEvent.createRemoveProtectionTape().isSubStepRunning).isTrue()
        assertThat(PatchLifecycleEvent.createRotateKnob().isSubStepRunning).isTrue()
        assertThat(PatchLifecycleEvent.createBasalSetting().isSubStepRunning).isTrue()
    }

    @Test
    fun `isSubStepRunning should be false for SHUTDOWN and ACTIVATED`() {
        assertThat(PatchLifecycleEvent.createShutdown().isSubStepRunning).isFalse()
        assertThat(PatchLifecycleEvent.createActivated().isSubStepRunning).isFalse()
    }

    @Test
    fun `isSafetyCheck should be true only for SAFETY_CHECK`() {
        assertThat(PatchLifecycleEvent.createSafetyCheck().isSafetyCheck).isTrue()
        assertThat(PatchLifecycleEvent.createShutdown().isSafetyCheck).isFalse()
        assertThat(PatchLifecycleEvent.createActivated().isSafetyCheck).isFalse()
    }

    @Test
    fun `isBasalSetting should be true only for BASAL_SETTING`() {
        assertThat(PatchLifecycleEvent.createBasalSetting().isBasalSetting).isTrue()
        assertThat(PatchLifecycleEvent.createShutdown().isBasalSetting).isFalse()
        assertThat(PatchLifecycleEvent.createActivated().isBasalSetting).isFalse()
    }

    @Test
    fun `isRotateKnob should be true only for ROTATE_KNOB`() {
        assertThat(PatchLifecycleEvent.createRotateKnob().isRotateKnob).isTrue()
        assertThat(PatchLifecycleEvent.createShutdown().isRotateKnob).isFalse()
        assertThat(PatchLifecycleEvent.createActivated().isRotateKnob).isFalse()
    }

    @Test
    fun `isShutdown should be true only for SHUTDOWN`() {
        assertThat(PatchLifecycleEvent.createShutdown().isShutdown).isTrue()
        assertThat(PatchLifecycleEvent.createBonded().isShutdown).isFalse()
        assertThat(PatchLifecycleEvent.createActivated().isShutdown).isFalse()
    }

    @Test
    fun `isActivated should be true only for ACTIVATED`() {
        assertThat(PatchLifecycleEvent.createActivated().isActivated).isTrue()
        assertThat(PatchLifecycleEvent.createShutdown().isActivated).isFalse()
        assertThat(PatchLifecycleEvent.createBonded().isActivated).isFalse()
    }

    @Test
    fun `timestamp should be set on creation`() {
        val beforeTime = System.currentTimeMillis()
        val event = PatchLifecycleEvent.createActivated()
        val afterTime = System.currentTimeMillis()

        assertThat(event.timestamp).isAtLeast(beforeTime)
        assertThat(event.timestamp).isAtMost(afterTime)
    }

    @Test
    fun `toString should contain key information`() {
        val event = PatchLifecycleEvent.createActivated()

        val stringRep = event.toString()

        assertThat(stringRep).contains("PatchLifecycleEvent")
        assertThat(stringRep).contains("lifeCycle=ACTIVATED")
        assertThat(stringRep).contains("timestamp=")
    }

    @Test
    fun `initObject should reset to shutdown`() {
        val event = PatchLifecycleEvent.createActivated()
        val originalTimestamp = event.timestamp

        // Wait a bit to ensure timestamp changes
        Thread.sleep(10)

        event.initObject()

        assertThat(event.lifeCycle).isEqualTo(PatchLifecycle.SHUTDOWN)
        assertThat(event.timestamp).isGreaterThan(originalTimestamp)
    }
}
