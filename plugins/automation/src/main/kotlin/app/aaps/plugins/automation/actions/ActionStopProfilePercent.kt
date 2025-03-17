package app.aaps.plugins.automation.actions

import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class ActionStopProfilePercent(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var persistenceLayer: PersistenceLayer

    override fun friendlyName(): Int = R.string.stopprofilepercent
    override fun shortDescription(): String = rh.gs(R.string.stopprofilepercent)
    override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        if (profileFunction.getProfile() == null) {
            aapsLogger.error(LTag.AUTOMATION, "ProfileFunctions not initialized")
            callback.result(instantiator.providePumpEnactResult().success(false).comment(app.aaps.core.ui.R.string.noprofile)).run()
            return
        }

        //TODO: remove this if it's not needed
        // var permPS= persistenceLayer.getPermanentProfileSwitchActiveAt(dateUtil.now())
        // if(permPS==null){
        //     aapsLogger.error(LTag.AUTOMATION, "Couldn't get permanent profile")
        //     callback.result(instantiator.providePumpEnactResult().success(false).comment(app.aaps.core.ui.R.string.noprofile)).run()
        //     return
        // }
        // profileFunction.getProfile()
        // val profileStore = activePlugin.activeProfileSource.profile ?: return
        // if (profileStore.getSpecificProfile(permPS.profileName) == null) {
        //     aapsLogger.error(LTag.AUTOMATION, "Selected profile does not exist! - ${permPS.profileName}")
        //     callback.result(instantiator.providePumpEnactResult().success(false).comment(app.aaps.core.ui.R.string.notexists)).run()
        //     return
        // }

        if (profileFunction.createProfileSwitch( durationInMinutes =0,
                                                 percentage= 100,
                                                 timeShiftInHours =  0,
                                                action = app.aaps.core.data.ue.Action.PROFILE_SWITCH,
                                                source= Sources.Automation,
                                                note=title + ": " + rh.gs(app.aaps.core.ui.R.string.startprofile, 100, 0),
                                                listValues = listOf(
                                                 ValueWithUnit.Percent(100),
                                                ValueWithUnit.Minute(0)
                                                ))) {

            callback.result(instantiator.providePumpEnactResult().success(true).comment(app.aaps.core.ui.R.string.ok)).run()
        } else {
            aapsLogger.error(LTag.AUTOMATION, "Final profile not valid")
            callback.result(instantiator.providePumpEnactResult().success(false).comment(app.aaps.core.ui.R.string.ok)).run()
        }
    }

    override fun isValid(): Boolean = true
}