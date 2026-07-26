package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.pump.PumpEnactResult
import dagger.android.HasAndroidInjector

// Used for instantiation of other actions only
class ActionDummy(injector: HasAndroidInjector) : Action(injector) {

    override fun friendlyName(): Int {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun shortDescription(): String {
        throw NotImplementedError("An operation is not implemented")
    }

    override suspend fun doAction(): PumpEnactResult {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun isValid(): Boolean = false
}
