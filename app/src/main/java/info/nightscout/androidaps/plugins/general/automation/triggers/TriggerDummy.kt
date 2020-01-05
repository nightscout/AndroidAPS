package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp

// Used for instantiation of other triggers only
class TriggerDummy(mainApp: MainApp) : Trigger(mainApp) {

    override fun shouldRun(): Boolean {
        throw NotImplementedError("An operation is not implemented")
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
        throw NotImplementedError("An operation is not implemented")
    }

    override fun icon(): Optional<Int?> {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun duplicate(): Trigger {
        throw NotImplementedError("An operation is not implemented")
    }
}