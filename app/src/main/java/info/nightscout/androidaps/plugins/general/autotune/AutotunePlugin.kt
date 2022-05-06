package info.nightscout.androidaps.plugins.general.autotune

import android.view.View
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.LocalInsulin
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.autotune.data.ATProfile
import info.nightscout.androidaps.plugins.general.autotune.data.PreppedGlucose
import info.nightscout.androidaps.plugins.general.autotune.events.EventAutotuneUpdateGui
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * adaptation from oref0 autotune started by philoul on 2020 (complete refactoring of AutotunePlugin initialised by Rumen Georgiev on 1/29/2018.)
 *
 * TODO: replace Thread by Worker
 * TODO: future version (once first version validated): add DIA and Peak tune for insulin
 * TODO: future version: Allow day of the week selection to tune specifics days (training days, working days, WE days)
 */

@Singleton
class AutotunePlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBus,
    private val profileFunction: ProfileFunction,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val localProfilePlugin: LocalProfilePlugin,
    private val autotuneFS: AutotuneFS,
    private val autotuneIob: AutotuneIob,
    private val autotunePrep: AutotunePrep,
    private val autotuneCore: AutotuneCore,
    private val buildHelper:BuildHelper,
    private val uel: UserEntryLogger,
    aapsLogger: AAPSLogger
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(AutotuneFragment::class.qualifiedName)
    .pluginIcon(R.drawable.ic_autotune)
    .pluginName(R.string.autotune)
    .shortName(R.string.autotune_shortname)
    .preferencesId(R.xml.pref_autotune)
    .description(R.string.autotune_description),
    aapsLogger, resourceHelper, injector
), Autotune {
    @Volatile override var lastRunSuccess: Boolean = false
    @Volatile var result: String = ""
    @Volatile var calculationRunning: Boolean = false
    @Volatile var lastRun: Long = 0
    @Volatile var selectedProfile = ""
    @Volatile var lastNbDays: String = ""
    @Volatile var updateButtonVisibility: Int = 0
    @Volatile lateinit var pumpProfile: ATProfile
    @Volatile var tunedProfile: ATProfile? = null
    private var preppedGlucose: PreppedGlucose? = null
    private lateinit var profile: Profile
    val autotuneStartHour: Int = 4

    override fun aapsAutotune(daysBack: Int, autoSwitch: Boolean, profileToTune: String): String {
        tunedProfile = null
        updateButtonVisibility = View.GONE
        lastRunSuccess = false
        var logResult = ""
        result = ""
        if (profileFunction.getProfile() == null) {
            result = rh.gs(R.string.profileswitch_ismissing)
            return result
        }
        val detailedLog = sp.getBoolean(R.string.key_autotune_additional_log, false)
        calculationRunning = true
        lastNbDays = "" + daysBack
        lastRun = dateUtil.now()
        val profileStore = activePlugin.activeProfileSource.profile ?: return rh.gs(R.string.profileswitch_ismissing)
        selectedProfile = if (profileToTune.isEmpty()) profileFunction.getProfileName() else profileToTune
        profileFunction.getProfile()?.let { currentProfile ->
            profile = profileStore.getSpecificProfile(profileToTune)?.let { ProfileSealed.Pure(it) } ?: currentProfile
        }
        var localInsulin = LocalInsulin("PumpInsulin", activePlugin.activeInsulin.peak, profile.dia) // var because localInsulin could be updated later with Tune Insulin peak/dia

        log("Start Autotune with $daysBack days back")
        autotuneFS.createAutotuneFolder()                           //create autotune subfolder for autotune files if not exists
        autotuneFS.deleteAutotuneFiles()                            //clean autotune folder before run
        // Today at 4 AM
        var endTime = MidnightTime.calc(lastRun) + autotuneStartHour * 60 * 60 * 1000L
        if (endTime > lastRun) endTime -= 24 * 60 * 60 * 1000L      // Check if 4 AM is before now
        val starttime = endTime - daysBack * 24 * 60 * 60 * 1000L
        autotuneFS.exportSettings(settings(lastRun, daysBack, starttime, endTime))
        tunedProfile = ATProfile(profile, localInsulin, injector).also {
            it.profilename = rh.gs(R.string.autotune_tunedprofile_name)
        }
        pumpProfile = ATProfile(profile, localInsulin, injector).also {
            it.profilename = selectedProfile
        }
        autotuneFS.exportPumpProfile(pumpProfile)

        for (i in 0 until daysBack) {
            val from = starttime + i * 24 * 60 * 60 * 1000L         // get 24 hours BG values from 4 AM to 4 AM next day
            val to = from + 24 * 60 * 60 * 1000L
            log("Tune day " + (i + 1) + " of " + daysBack)
            tunedProfile?.let { tunedProfile ->
                autotuneIob.initializeData(from, to, tunedProfile)  //autotuneIob contains BG and Treatments data from history (<=> query for ns-treatments and ns-entries)
                autotuneFS.exportEntries(autotuneIob)               //<=> ns-entries.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine
                autotuneFS.exportTreatments(autotuneIob)            //<=> ns-treatments.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine (include treatments ,tempBasal and extended
                preppedGlucose = autotunePrep.categorizeBGDatums(tunedProfile, localInsulin) //<=> autotune.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine
            }

            if (preppedGlucose == null || tunedProfile == null) {
                result = rh.gs(R.string.autotune_error)
                log(result)
                calculationRunning = false
                rxBus.send(EventAutotuneUpdateGui())
                tunedProfile = null
                autotuneFS.exportResult(result)
                autotuneFS.exportLogAndZip(lastRun)
                return result
            }
            preppedGlucose?.let { preppedGlucose ->         //preppedGlucose and tunedProfile should never be null here
                autotuneFS.exportPreppedGlucose(preppedGlucose)
                tunedProfile = autotuneCore.tuneAllTheThings(preppedGlucose, tunedProfile!!, pumpProfile)
            }
            // localInsulin = LocalInsulin("TunedInsulin", tunedProfile!!.peak, tunedProfile!!.dia)     // Todo: Add tune Insulin option
            autotuneFS.exportTunedProfile(tunedProfile!!)   //<=> newprofile.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine
            if (i < daysBack - 1) {
                log("Partial result for day ${i + 1}".trimIndent())
                result = rh.gs(R.string.autotune_partial_result, i + 1, daysBack)
                rxBus.send(EventAutotuneUpdateGui())
            }
            logResult = showResults(tunedProfile, pumpProfile)
            if (detailedLog)
                autotuneFS.exportLog(lastRun, i + 1)
        }
        result = rh.gs(R.string.autotune_result, dateUtil.dateAndTimeString(lastRun))
        if (!detailedLog)
            autotuneFS.exportLog(lastRun)
        autotuneFS.exportResult(logResult)
        autotuneFS.zipAutotune(lastRun)
        updateButtonVisibility = View.VISIBLE

        if (autoSwitch) {
            val circadian = sp.getBoolean(R.string.key_autotune_circadian_ic_isf, false)
            tunedProfile?.let { tunedP ->
                tunedP.profilename = pumpProfile.profilename
                updateProfile(tunedP)
                uel.log(
                    UserEntry.Action.STORE_PROFILE,
                    UserEntry.Sources.Autotune,
                    ValueWithUnit.SimpleString(tunedP.profilename)
                )
                updateButtonVisibility = View.GONE
                tunedP.profileStore(circadian)?.let { profilestore ->
                    if (profileFunction.createProfileSwitch(
                            profilestore,
                            profileName = tunedP.profilename,
                            durationInMinutes = 0,
                            percentage = 100,
                            timeShiftInHours = 0,
                            timestamp = dateUtil.now()
                        )
                    ) {
                        log("Profile Switch succeed ${tunedP.profilename}")
                        uel.log(
                            UserEntry.Action.PROFILE_SWITCH,
                            UserEntry.Sources.Autotune,
                            "Autotune AutoSwitch",
                            ValueWithUnit.SimpleString(tunedP.profilename))
                    }
                    rxBus.send(EventLocalProfileChanged())
                }
            }
        }
        lastRunSuccess = true
        sp.putLong(R.string.key_autotune_last_run, lastRun)
        rxBus.send(EventAutotuneUpdateGui())
        calculationRunning = false
        tunedProfile?.let {
            return result
        }
        return "No Result"  // should never occurs
    }

    private fun showResults(tunedProfile: ATProfile?, pumpProfile: ATProfile): String {
        if (tunedProfile == null)
            return "No Result"  // should never occurs
        val line = rh.gs(R.string.autotune_log_separator)
        var strResult = line
        strResult += rh.gs(R.string.autotune_log_title)
        strResult += line
        // show ISF and CR
        strResult += rh.gs(R.string.autotune_log_isf, rh.gs(R.string.isf_short), pumpProfile.isf, tunedProfile.isf)
        strResult += rh.gs(R.string.autotune_log_ic, rh.gs(R.string.ic_short), pumpProfile.ic, tunedProfile.ic)
        strResult += line
        var totalBasal = 0.0
        var totalTuned = 0.0
        for (i in 0..23) {
            totalBasal += pumpProfile.basal[i]
            totalTuned += tunedProfile.basal[i]
            val percentageChangeValue = tunedProfile.basal[i] / pumpProfile.basal[i] * 100 - 100
            strResult += rh.gs(R.string.autotune_log_basal, i.toDouble(), pumpProfile.basal[i], tunedProfile.basal[i], tunedProfile.basalUntuned[i], percentageChangeValue)
        }
        strResult += line
        strResult += rh.gs(R.string.autotune_log_sum_basal, totalBasal, totalTuned)
        strResult += line
        log(strResult)
        return strResult
    }

    private fun settings(runDate: Long, nbDays: Int, firstloopstart: Long, lastloopend: Long): String {
        var jsonString = ""
        val jsonSettings = JSONObject()
        val insulinInterface = activePlugin.activeInsulin
        val utcOffset = T.msecs(TimeZone.getDefault().getOffset(dateUtil.now()).toLong()).hours()
        val startDateString = dateUtil.toISOString(firstloopstart).substring(0,10)
        val endDateString = dateUtil.toISOString(lastloopend - 24 * 60 * 60 * 1000L).substring(0,10)
        val nsUrl = sp.getString(R.string.key_nsclientinternal_url, "")
        val optCategorizeUam = if (sp.getBoolean(R.string.key_autotune_categorize_uam_as_basal, false)) "-c=true" else ""
        val optInsulinCurve = ""
        try {
            jsonSettings.put("datestring", dateUtil.toISOString(runDate))
            jsonSettings.put("dateutc", dateUtil.toISOAsUTC(runDate))
            jsonSettings.put("utcOffset", utcOffset)
            jsonSettings.put("units", profileFunction.getUnits().asText)
            jsonSettings.put("timezone", TimeZone.getDefault().id)
            jsonSettings.put("url_nightscout", sp.getString(R.string.key_nsclientinternal_url, ""))
            jsonSettings.put("nbdays", nbDays)
            jsonSettings.put("startdate", startDateString)
            jsonSettings.put("enddate", endDateString)
            // command to change timezone
            jsonSettings.put("timezone_command", "sudo ln -sf /usr/share/zoneinfo/" + TimeZone.getDefault().id + " /etc/localtime")
            // oref0_command is for running oref0-autotune on a virtual machine in a dedicated ~/aaps subfolder
            jsonSettings.put("oref0_command", "oref0-autotune -d=~/aaps -n=$nsUrl -s=$startDateString -e=$endDateString $optCategorizeUam $optInsulinCurve")
            // aaps_command is for running modified oref0-autotune with exported data from aaps (ns-entries and ns-treatment json files copied in ~/aaps/autotune folder and pumpprofile.json copied in ~/aaps/settings/
            jsonSettings.put("aaps_command", "aaps-autotune -d=~/aaps -s=$startDateString -e=$endDateString $optCategorizeUam $optInsulinCurve")
            jsonSettings.put("categorize_uam_as_basal", sp.getBoolean(R.string.key_autotune_categorize_uam_as_basal, false))
            jsonSettings.put("tune_insulin_curve", false)

            val peaktime: Int = insulinInterface.peak
            if (insulinInterface.id === Insulin.InsulinType.OREF_ULTRA_RAPID_ACTING)
                jsonSettings.put("curve","ultra-rapid")
            else if (insulinInterface.id === Insulin.InsulinType.OREF_RAPID_ACTING)
                jsonSettings.put("curve", "rapid-acting")
            else if (insulinInterface.id === Insulin.InsulinType.OREF_LYUMJEV) {
                jsonSettings.put("curve", "ultra-rapid")
                jsonSettings.put("useCustomPeakTime", true)
                jsonSettings.put("insulinPeakTime", peaktime)
            } else if (insulinInterface.id === Insulin.InsulinType.OREF_FREE_PEAK) {
                jsonSettings.put("curve", if (peaktime > 55) "rapid-acting" else "ultra-rapid")
                jsonSettings.put("useCustomPeakTime", true)
                jsonSettings.put("insulinPeakTime", peaktime)
            }
            jsonString = jsonSettings.toString(4).replace("\\/", "/")
        } catch (e: JSONException) { }
        return jsonString
    }

    fun updateProfile(newProfile: ATProfile?) {
        if (newProfile == null) return
        val circadian = sp.getBoolean(R.string.key_autotune_circadian_ic_isf, false)
        val profileStore = activePlugin.activeProfileSource.profile ?: ProfileStore(injector, JSONObject(), dateUtil)
        val profileList: ArrayList<CharSequence> = profileStore.getProfileList()
        var indexLocalProfile = -1
        for (p in profileList.indices)
            if (profileList[p] == newProfile.profilename)
                indexLocalProfile = p
        if (indexLocalProfile == -1) {
            localProfilePlugin.addProfile(localProfilePlugin.copyFrom(newProfile.getProfile(circadian), newProfile.profilename))
            return
        }
        localProfilePlugin.currentProfileIndex = indexLocalProfile
        localProfilePlugin.currentProfile()?.dia = newProfile.dia
        localProfilePlugin.currentProfile()?.basal = newProfile.basal()
        localProfilePlugin.currentProfile()?.ic = newProfile.ic(circadian)
        localProfilePlugin.currentProfile()?.isf = newProfile.isf(circadian)
        localProfilePlugin.storeSettings()
    }


    private fun log(message: String) {
        atLog("[Plugin] $message")
    }

    override fun specialEnableCondition(): Boolean = buildHelper.isEngineeringMode() && buildHelper.isDev()

    override fun atLog(message: String) {
        autotuneFS.atLog(message)
    }
}