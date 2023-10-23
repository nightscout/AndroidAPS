package app.aaps.plugins.automation.triggers

import dagger.android.HasAndroidInjector
import org.json.JSONObject
import java.util.Optional

// Used for instantiation of other triggers only
class TriggerDummy(injector: HasAndroidInjector, val shouldRun: Boolean = false) : Trigger(injector) {

    override fun shouldRun(): Boolean {
        return shouldRun
    }

    override fun dataJSON(): JSONObject {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun fromJSON(data: String): Trigger = TriggerDummy(injector)

    override fun friendlyName(): Int {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun friendlyDescription(): String {
        return "TriggerDummy"
    }

    override fun icon(): Optional<Int> {
        throw NotImplementedError("An operation is not implemented")
    }

    override fun duplicate(): Trigger {
        throw NotImplementedError("An operation is not implemented")
    }
}