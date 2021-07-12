package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.json.JSONObject

class DummyTrigger(var result: Boolean) : Trigger(HasAndroidInjector { AndroidInjector { } }) {

    override fun shouldRun(): Boolean = result
    override fun dataJSON(): JSONObject = JSONObject()
    override fun fromJSON(data: String): Trigger = DummyTrigger(result)
    override fun friendlyName(): Int = 0
    override fun friendlyDescription(): String = " "
    override fun icon(): Optional<Int?> = Optional.absent()
    override fun duplicate(): Trigger = DummyTrigger(result)
}