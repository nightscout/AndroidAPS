package app.aaps.pump.eopatch.vo

import app.aaps.pump.eopatch.code.PatchLifecycle
import com.google.android.gms.common.internal.Preconditions

class PatchLifecycleEvent {

    var lifeCycle: PatchLifecycle = PatchLifecycle.SHUTDOWN

    var timestamp = 0L

    val isSafetyCheck: Boolean
        get() = this.lifeCycle == PatchLifecycle.SAFETY_CHECK

    val isBasalSetting: Boolean
        get() = this.lifeCycle == PatchLifecycle.BASAL_SETTING

    val isSubStepRunning: Boolean
        get() = this.lifeCycle.rawValue > PatchLifecycle.SHUTDOWN.rawValue && this.lifeCycle.rawValue < PatchLifecycle.ACTIVATED.rawValue

    val isRotateKnob: Boolean
        get() = this.lifeCycle == PatchLifecycle.ROTATE_KNOB

    val isShutdown: Boolean
        get() = this.lifeCycle.rawValue == PatchLifecycle.SHUTDOWN.rawValue

    val isActivated: Boolean
        get() = this.lifeCycle == PatchLifecycle.ACTIVATED

    constructor() {
        initObject()
    }

    fun initObject() {
        this.lifeCycle = PatchLifecycle.SHUTDOWN
        this.timestamp = System.currentTimeMillis()
    }

    override fun toString(): String {
        return "PatchLifecycleEvent(lifeCycle=$lifeCycle, timestamp=$timestamp)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PatchLifecycleEvent
        // Compare only lifeCycle, not timestamp, as timestamps can differ for equivalent states
        return lifeCycle == other.lifeCycle
    }

    override fun hashCode(): Int {
        return lifeCycle.hashCode()
    }

    constructor(lifeCycle: PatchLifecycle) {
        Preconditions.checkNotNull(lifeCycle)
        this.lifeCycle = lifeCycle
        this.timestamp = System.currentTimeMillis()
    }

    companion object {

        fun create(lifecycle: PatchLifecycle): PatchLifecycleEvent {
            Preconditions.checkNotNull(lifecycle)

            return PatchLifecycleEvent(lifecycle)
        }

        @JvmStatic
        fun createShutdown(): PatchLifecycleEvent {
            return PatchLifecycleEvent(PatchLifecycle.SHUTDOWN)
        }

        @JvmStatic
        fun createBonded(): PatchLifecycleEvent {
            return PatchLifecycleEvent(PatchLifecycle.BONDED)
        }

        @JvmStatic
        fun createRemoveNeedleCap(): PatchLifecycleEvent {
            return PatchLifecycleEvent(PatchLifecycle.REMOVE_NEEDLE_CAP)
        }

        @JvmStatic
        fun createRemoveProtectionTape(): PatchLifecycleEvent {
            return PatchLifecycleEvent(PatchLifecycle.REMOVE_PROTECTION_TAPE)
        }

        @JvmStatic
        fun createSafetyCheck(): PatchLifecycleEvent {
            return PatchLifecycleEvent(PatchLifecycle.SAFETY_CHECK)
        }

        @JvmStatic
        fun createRotateKnob(): PatchLifecycleEvent {
            return PatchLifecycleEvent(PatchLifecycle.ROTATE_KNOB)
        }

        @JvmStatic
        fun createBasalSetting(): PatchLifecycleEvent {
            return PatchLifecycleEvent(PatchLifecycle.BASAL_SETTING)
        }

        @JvmStatic
        fun createActivated(): PatchLifecycleEvent {
            return PatchLifecycleEvent(PatchLifecycle.ACTIVATED)
        }
    }
}
