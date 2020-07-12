package info.nightscout.androidaps.plugins.general.autotune

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.automation.AutomationEvent
import info.nightscout.androidaps.plugins.general.automation.dialogs.EditEventDialog
import info.nightscout.androidaps.plugins.general.autotune.data.ATProfile
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin.SingleProfile
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import kotlinx.android.synthetic.main.autotune_fragment.*
import java.util.*
import javax.inject.Inject

/**
 * Created by Rumen Georgiev on 1/29/2018.
 * Deep rework by philoul on 06/2020
 */
// Todo: Reset results field and Switch/Copy button visibility when Nb of selected days is changed
class AutotuneFragment : DaggerFragment() {
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var autotunePlugin: AutotunePlugin
    @Inject lateinit var sp: SP
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var nsProfilePlugin: NSProfilePlugin
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var rxBus: RxBusWrapper

    private var lastRun: Date? = null
    private var lastRunTxt: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.autotune_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        autotune_run.setOnClickListener {
            val daysBack = tune_days.text.toString().toInt()
            if (daysBack > 0) {
                tune_result.text = autotunePlugin.aapsAutotune(daysBack, false)
                autotune_profileswitch.visibility = AutotunePlugin.profileSwitchButtonVisibility
                autotune_copylocal.visibility = AutotunePlugin.copyButtonVisibility
                tune_warning.text = resourceHelper.gs(R.string.autotune_warning_after_run)
                lastRunTxt = if (AutotunePlugin.lastRun != null) "" + dateUtil.dateAndTimeString(AutotunePlugin.lastRun) else ""
                tune_lastrun.text = lastRunTxt
                lastRun = if (AutotunePlugin.lastRun != null) AutotunePlugin.lastRun else Date(0)
            } else tune_result.text = resourceHelper.gs(R.string.autotune_min_days)
        }

        autotune_copylocal.setOnClickListener {
            val localName = resourceHelper.gs(R.string.autotune_tunedprofile_name) + " " + dateUtil.dateAndTimeString(AutotunePlugin.lastRun)
            showConfirmation(requireContext(), resourceHelper.gs(R.string.autotune_copy_localprofile_button), """
                ${resourceHelper.gs(R.string.autotune_copy_local_profile_message)}
                $localName
                ${dateUtil.dateAndTimeString(lastRun)}
                """.trimIndent(), Runnable {
                    localProfilePlugin.addProfile(SingleProfile().copyFrom(localProfilePlugin.createProfileStore(), AutotunePlugin.tunedProfile!!.getProfile(), localName))
                    rxBus.send(EventLocalProfileChanged())
                    autotune_copylocal.visibility = View.GONE
                    AutotunePlugin.copyButtonVisibility = View.GONE
                }
            )
        }

        autotune_profileswitch.setOnClickListener{
            val name = resourceHelper.gs(R.string.autotune_tunedprofile_name)
            val profileStore = AutotunePlugin.tunedProfile!!.profileStore
            log("ProfileSwitch pressed")
            if (profileStore != null) {
                showConfirmation(requireContext(), resourceHelper.gs(R.string.activate_profile) + ": " + AutotunePlugin.tunedProfile!!.profilename + " ?", Runnable {
                    activePlugin.activeTreatments.doProfileSwitch(AutotunePlugin.tunedProfile!!.profileStore, AutotunePlugin.tunedProfile!!.profilename, 0, 100, 0, DateUtil.now())
                    rxBus.send(EventLocalProfileChanged())
                    autotune_profileswitch.visibility = View.GONE
                    AutotunePlugin.profileSwitchButtonVisibility = View.GONE
                })
            } else log("ProfileStore is null!")
        }

        autotune_copylocal.visibility = View.GONE
        autotune_profileswitch.visibility = View.GONE

        lastRun = if (AutotunePlugin.lastRun != null) AutotunePlugin.lastRun else Date(0)
        if (lastRun!!.time > MidnightTime.calc(System.currentTimeMillis() - AutotunePlugin.autotuneStartHour * 3600 * 1000L) + AutotunePlugin.autotuneStartHour * 3600 * 1000L && AutotunePlugin.result !== "") {
            tune_warning.text = resourceHelper.gs(R.string.autotune_warning_after_run)
            tune_days.setText(AutotunePlugin.lastNbDays)
            tune_result.text = AutotunePlugin.result
            autotune_copylocal.visibility = AutotunePlugin.copyButtonVisibility
            autotune_profileswitch.visibility = AutotunePlugin.profileSwitchButtonVisibility
        } else { //if new day reinit result, default days, warning and button's visibility
            tune_warning.text = addWarnings()
            tune_days.setText(sp!!.getString(R.string.key_autotune_default_tune_days, "5"))
            tune_result.text = ""
            AutotunePlugin.profileSwitchButtonVisibility = View.GONE
            AutotunePlugin.copyButtonVisibility = View.GONE
        }
        lastRunTxt = if (AutotunePlugin.lastRun != null) dateUtil.dateAndTimeString(AutotunePlugin.lastRun) else ""
        tune_lastrun.text = lastRunTxt
        updateGUI()
    }

    // disabled by philoul to build AAPS
    //@Override
    protected fun updateGUI() {
        val activity: Activity? = activity
        activity?.runOnUiThread { }
    }

    private fun addWarnings(): String {
        var warning = resourceHelper.gs(R.string.autotune_warning_before_run)
        var nl = "\n"
        val profile = ATProfile(profileFunction.getProfile(System.currentTimeMillis()))
        if (!profile.isValid) return resourceHelper.gs(R.string.autotune_profile_invalid)
        if (profile.icSize > 1) {
            //warning = nl + "Autotune works with only one IC value, your profile has " + profile.getIcSize() + " values. Average value is " + profile.ic + "g/U";
            warning = nl + resourceHelper.gs(R.string.format_autotune_ic_warning, profile.icSize, profile.ic)
            nl = "\n"
        }
        if (profile.isfSize > 1) {
            //warning = nl + "Autotune works with only one ISF value, your profile has " + profile.getIsfSize() + " values. Average value is " + profile.isf/toMgDl + profileFunction.getUnits() + "/U";
            warning = nl + resourceHelper.gs(R.string.format_autotune_isf_warning, profile.isfSize, Profile.fromMgdlToUnits(profile.isf, profileFunction.getUnits()), profileFunction.getUnits())
            nl = "\n"
        }
        return warning
    }

    //update if possible AutotuneFragment at beginning and during calculation between each day
    fun updateResult(message: String?) {
        tune_result.text = message
        updateGUI()
    }

    private fun log(message: String) {
        autotunePlugin.atLog("[Fragment] $message")
    }
}