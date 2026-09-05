package app.aaps.plugins.automation.triggers

import app.aaps.core.keys.interfaces.TextRef
import kotlinx.serialization.json.JsonObject

// Used for instantiation of other triggers only
class TriggerDummy(deps: TriggerDeps, val shouldRun: Boolean = false) : Trigger(deps) {

    override suspend fun shouldRun(): Boolean {
        return shouldRun
    }

    override fun dataJSON(): JsonObject {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun fromJSON(data: String): Trigger = TriggerDummy(deps)

    override fun friendlyName(): TextRef {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun friendlyDescription(): String {
        return "TriggerDummy"
    }

    override fun duplicate(): Trigger {
        throw NotImplementedError("An operation is not implemented")
    }
}