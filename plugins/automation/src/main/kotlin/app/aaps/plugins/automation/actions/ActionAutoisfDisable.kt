package app.aaps.plugins.automation.actions

import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.automation.R
import javax.inject.Inject

class ActionAutoisfDisable(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var sp: SP

    override fun friendlyName(): Int = R.string.disableautoisf
    override fun shortDescription(): String = rh.gs(R.string.disableautoisf)
    @DrawableRes override fun icon(): Int = R.drawable.ic_autoisf_disabled

    override fun doAction(callback: Callback) {
        val currentAutoisfStatus: Boolean = sp.getBoolean(R.string.enable_autoISF, false)
        if (currentAutoisfStatus) {
            uel.log(app.aaps.core.data.ue.Action.AUTOISF_DISABLED, Sources.Automation, title)
            sp.putBoolean(R.string.enable_autoISF, false)
            callback.result(instantiator.providePumpEnactResult().success(true).comment(R.string.autoisf_disabled)).run()
        } else {
            callback.result(instantiator.providePumpEnactResult().success(true).comment(R.string.autoisf_alreadydisabled)).run()
        }
    }

    override fun isValid(): Boolean = true

    override fun hasDialog(): Boolean = false

}
