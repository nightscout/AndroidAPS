package app.aaps.plugins.automation.triggers

import org.json.JSONObject

// Used for instantiation of other triggers only
class TriggerDummy(deps: TriggerDeps, val shouldRun: Boolean = false) : Trigger(deps) {

    override suspend fun shouldRun(): Boolean {
        return shouldRun
    }

    override fun dataJSON(): JSONObject {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun fromJSON(data: String): Trigger = TriggerDummy(deps)

    override fun friendlyName(): Int {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun friendlyDescription(): String {
        return "TriggerDummy"
    }

    override fun duplicate(): Trigger {
        throw NotImplementedError("An operation is not implemented")
    }
}