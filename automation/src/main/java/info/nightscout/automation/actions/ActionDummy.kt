package info.nightscout.automation.actions

import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.queue.Callback

// Used for instantiation of other actions only
class ActionDummy(injector: HasAndroidInjector) : Action(injector) {

    override fun friendlyName(): Int {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun shortDescription(): String {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun doAction(callback: Callback) {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun isValid(): Boolean = false

    override fun icon(): Int {
        throw NotImplementedError("An operation is not implemented")
    }
}