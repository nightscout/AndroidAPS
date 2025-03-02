package app.aaps.plugins.automation.actions

import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.queue.Callback
//import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.Preferences
import app.aaps.plugins.automation.R
import javax.inject.Inject

class ActionAutoisfEnable(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var uel: UserEntryLogger
    //@Inject lateinit var sp: SP
    @Inject lateinit var preferences: Preferences

    override fun friendlyName(): Int = R.string.enableautoisf
    override fun shortDescription(): String = rh.gs(R.string.enableautoisf)
    @DrawableRes override fun icon(): Int = R.drawable.ic_autoisf_enabled

    override fun doAction(callback: Callback) {
        //val currentAutoisfStatus:Boolean = sp.getBoolean(R.string.enable_autoISF, true)
        val currentAutoisfStatus:Boolean = preferences.get(BooleanKey.ApsUseAutoIsfWeights)
        if (!currentAutoisfStatus) {
            uel.log(app.aaps.core.data.ue.Action.AUTOISF_ENABLED, Sources.Automation, title)
            //sp.putBoolean(R.string.enable_autoISF, true)
            preferences.put(BooleanKey. ApsUseAutoIsfWeights, true)
            callback.result(instantiator.providePumpEnactResult().success(true).comment(R.string.autoisf_enabled)).run()
        } else {
            callback.result(instantiator.providePumpEnactResult().success(true).comment(R.string.autoisf_alreadyenabled)).run()
        }
    }

    override fun isValid(): Boolean = true

    override fun hasDialog(): Boolean = false
}