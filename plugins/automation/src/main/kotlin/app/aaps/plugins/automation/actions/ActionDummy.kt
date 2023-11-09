package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.queue.Callback
import dagger.android.HasAndroidInjector

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