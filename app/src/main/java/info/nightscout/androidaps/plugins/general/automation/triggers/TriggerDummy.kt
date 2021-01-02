package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import dagger.android.HasAndroidInjector

// Used for instantiation of other triggers only
class TriggerDummy(injector: HasAndroidInjector, val shouldRun: Boolean = false) : Trigger(injector) {

    override fun shouldRun(): Boolean {
        return shouldRun
    }

    override fun toJSON(): String {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun fromJSON(data: String): Trigger {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun friendlyName(): Int {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun friendlyDescription(): String {
        return "TriggerDummy"
    }

    override fun icon(): Optional<Int?> {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun duplicate(): Trigger {
        throw NotImplementedError("An operation is not implemented")
    }
}