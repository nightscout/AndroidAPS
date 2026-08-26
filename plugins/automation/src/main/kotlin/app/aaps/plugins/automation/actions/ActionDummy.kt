package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import javax.inject.Provider

// Used for instantiation of other actions only
class ActionDummy(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    pumpEnactResultProvider: Provider<PumpEnactResult>
) : Action(aapsLogger, rh, pumpEnactResultProvider) {

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
