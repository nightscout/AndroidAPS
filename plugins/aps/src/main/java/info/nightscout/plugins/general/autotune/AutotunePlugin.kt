package info.nightscout.plugins.general.autotune

import android.view.View
import dagger.android.HasAndroidInjector
import info.nightscout.automation.elements.InputWeekDay
import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.autotune.Autotune
import info.nightscout.interfaces.insulin.Insulin
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginDescription
import info.nightscout.interfaces.plugin.PluginType
import info.nightscout.interfaces.profile.Instantiator
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.interfaces.utils.MidnightTime
import info.nightscout.plugins.aps.R
import info.nightscout.plugins.general.autotune.data.ATProfile
import info.nightscout.plugins.general.autotune.data.LocalInsulin
import info.nightscout.plugins.general.autotune.data.PreppedGlucose
import info.nightscout.plugins.general.autotune.events.EventAutotuneUpdateGui
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventLocalProfileChanged
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import org.json.JSONException
import org.json.JSONObject
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.ArrayList

/*
 * adaptation from oref0 autotune developed by philoul on 2022 (complete refactoring of AutotunePlugin initialised by Rumen Georgiev on 1/29/2018.)
 *
 * TODO: replace Thread by Worker
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
    private val autotuneFS: AutotuneFS,
    private val autotuneIob: AutotuneIob,
    private val autotunePrep: AutotunePrep,
    private val autotuneCore: AutotuneCore,
    private val config: Config,
    private val uel: UserEntryLogger,
    aapsLogger: AAPSLogger,
    private val instantiator: Instantiator
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(AutotuneFragment::class.qualifiedName)
        .pluginIcon(info.nightscout.core.main.R.drawable.ic_autotune)
        .pluginName(info.nightscout.core.ui.R.string.autotune)
        .shortName(info.nightscout.core.ui.R.string.autotune_shortname)
        .preferencesId(R.xml.pref_autotune)
        .showInList(config.isEngineeringMode() && config.isDev())
        .description(info.nightscout.core.ui.R.string.autotune_description),
    aapsLogger, resourceHelper, injector
), Autotune {

    @Volatile override var lastRunSuccess: Boolean = false
    @Volatile var result: String = ""
    @Volatile override var calculationRunning: Boolean = false
    @Volatile var lastRun: Long = 0
    @Volatile var selectedProfile = ""
    @Volatile var lastNbDays: String = ""
    @Volatile var updateButtonVisibility: Int = 0
    @Volatile lateinit var pumpProfile: ATProfile
    @Volatile var tunedProfile: ATProfile? = null
    private var preppedGlucose: PreppedGlucose? = null
    private lateinit var profile: Profile
    val days = InputWeekDay()
    val autotuneStartHour: Int = 4

    override fun aapsAutotune(daysBack: Int, autoSwitch: Boolean, profileToTune: String, weekDays: BooleanArray?) {
        lastRunSuccess = false
        if (calculationRunning) {
            aapsLogger.debug(LTag.AUTOMATION, "Autotune run detected, Autotune Run Cancelled")
            return
        }
        calculationRunning = true
        weekDays?.let {
            for (i in weekDays.indices)
                days.weekdays[i] = weekDays[i]
        }
        val calcDays = calcDays(daysBack)
        val sb = StringBuilder()
        sb.append("Selected days: ")
        var counter = 0
        for (i in days.getSelectedDays()) {
            if (counter++ > 0) sb.append(",")
            sb.append(InputWeekDay.DayOfWeek.fromCalendarInt(i))
        }
        log(sb.toString())
        tunedProfile = null
        updateButtonVisibility = View.GONE
        var logResult = ""
        result = ""
        if (profileFunction.getProfile() == null) {
            result = rh.gs(info.nightscout.core.ui.R.string.profileswitch_ismissing)
            rxBus.send(EventAutotuneUpdateGui())
            calculationRunning = false
            return
        }
        val detailedLog = sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_additional_log, false)
        calculationRunning = true
        lastNbDays = "" + daysBack
        lastRun = dateUtil.now()
        val profileStore = activePlugin.activeProfileSource.profile
        if (profileStore == null) {
            result = rh.gs(info.nightscout.core.ui.R.string.profileswitch_ismissing)
            rxBus.send(EventAutotuneUpdateGui())
            calculationRunning = false
            return
        }
        selectedProfile = profileToTune.ifEmpty { profileFunction.getProfileName() }
        profileFunction.getProfile()?.let { currentProfile ->
            profile = profileStore.getSpecificProfile(profileToTune)?.let { ProfileSealed.Pure(it) } ?: currentProfile
        }
        val localInsulin = LocalInsulin("PumpInsulin", activePlugin.activeInsulin.peak, profile.dia) // var because localInsulin could be updated later with Tune Insulin peak/dia

        log("Start Autotune with $daysBack days back")
        autotuneFS.createAutotuneFolder()                           //create autotune subfolder for autotune files if not exists
        autotuneFS.deleteAutotuneFiles()                            //clean autotune folder before run
        // Today at 4 AM
        var endTime = MidnightTime.calc(lastRun) + autotuneStartHour * 60 * 60 * 1000L
        if (endTime > lastRun) endTime -= 24 * 60 * 60 * 1000L      // Check if 4 AM is before now
        val starttime = endTime - daysBack * 24 * 60 * 60 * 1000L
        autotuneFS.exportSettings(settings(lastRun, daysBack, starttime, endTime))
        tunedProfile = ATProfile(profile, localInsulin, injector).also {
            it.profileName = rh.gs(info.nightscout.core.ui.R.string.autotune_tunedprofile_name)
        }
        pumpProfile = ATProfile(profile, localInsulin, injector).also {
            it.profileName = selectedProfile
        }
        autotuneFS.exportPumpProfile(pumpProfile)

        if (calcDays==0) {
            result = rh.gs(info.nightscout.core.ui.R.string.autotune_error_more_days)
            log(result)
            calculationRunning = false
            tunedProfile = null
            autotuneFS.exportResult(result)
            autotuneFS.exportLogAndZip(lastRun)
            rxBus.send(EventAutotuneUpdateGui())
            return
        }
        var currentCalcDay = 0
        for (i in 0 until daysBack) {
            val from = starttime + i * 24 * 60 * 60 * 1000L         // get 24 hours BG values from 4 AM to 4 AM next day
            val to = from + 24 * 60 * 60 * 1000L
            if (days.isSet(from)) {
                currentCalcDay++

                log("Tune day " + (i + 1) + " of " + daysBack + " (" + currentCalcDay + " of " + calcDays + ")")
                tunedProfile?.let {
                    autotuneIob.initializeData(from, to, it)  //autotuneIob contains BG and Treatments data from history (<=> query for ns-treatments and ns-entries)
                    if (autotuneIob.boluses.size == 0) {
                        result = rh.gs(info.nightscout.core.ui.R.string.autotune_error)
                        log("No basal data on day ${i + 1}")
                        autotuneFS.exportResult(result)
                        autotuneFS.exportLogAndZip(lastRun)
                        rxBus.send(EventAutotuneUpdateGui())
                        calculationRunning = false
                        return
                    }
                    autotuneFS.exportEntries(autotuneIob)               //<=> ns-entries.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine
                    autotuneFS.exportTreatments(autotuneIob)            //<=> ns-treatments.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine (include treatments ,tempBasal and extended
                    preppedGlucose = autotunePrep.categorize(it) //<=> autotune.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine
                    preppedGlucose?.let { preppedGlucose ->         //preppedGlucose and tunedProfile should never be null here
                        autotuneFS.exportPreppedGlucose(preppedGlucose)
                        tunedProfile = autotuneCore.tuneAllTheThings(preppedGlucose, it, pumpProfile).also { tunedProfile ->
                            autotuneFS.exportTunedProfile(tunedProfile)   //<=> newprofile.yyyymmdd.json files exported for results compare with oref0 autotune on virtual machine
                            if (currentCalcDay < calcDays) {
                                log("Partial result for day ${i + 1}".trimIndent())
                                result = rh.gs(info.nightscout.core.ui.R.string.autotune_partial_result, currentCalcDay, calcDays)
                                rxBus.send(EventAutotuneUpdateGui())
                            }
                            logResult = showResults(tunedProfile, pumpProfile)
                            if (detailedLog)
                                autotuneFS.exportLog(lastRun, i + 1)
                        }
                    }
                        ?: {
                            log("preppedGlucose is null on day ${i + 1}")
                            tunedProfile = null
                        }
                }
                if (tunedProfile == null) {
                    result = rh.gs(info.nightscout.core.ui.R.string.autotune_error)
                    log("TunedProfile is null on day ${i + 1}")
                    autotuneFS.exportResult(result)
                    autotuneFS.exportLogAndZip(lastRun)
                    rxBus.send(EventAutotuneUpdateGui())
                    calculationRunning = false
                    return
                }
            }
        }
        result = rh.gs(info.nightscout.core.ui.R.string.autotune_result, dateUtil.dateAndTimeString(lastRun))
        if (!detailedLog)
            autotuneFS.exportLog(lastRun)
        autotuneFS.exportResult(logResult)
        autotuneFS.zipAutotune(lastRun)
        updateButtonVisibility = View.VISIBLE

        if (autoSwitch) {
            val circadian = sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_circadian_ic_isf, false)
            tunedProfile?.let { tunedP ->
                tunedP.profileName = pumpProfile.profileName
                updateProfile(tunedP)
                uel.log(
                    UserEntry.Action.STORE_PROFILE,
                    UserEntry.Sources.Automation,
                    rh.gs(info.nightscout.core.ui.R.string.autotune),
                    ValueWithUnit.SimpleString(tunedP.profileName)
                )
                updateButtonVisibility = View.GONE
                tunedP.profileStore(circadian)?.let { profilestore ->
                    if (profileFunction.createProfileSwitch(
                            profilestore,
                            profileName = tunedP.profileName,
                            durationInMinutes = 0,
                            percentage = 100,
                            timeShiftInHours = 0,
                            timestamp = dateUtil.now()
                        )
                    ) {
                        log("Profile Switch succeed ${tunedP.profileName}")
                        uel.log(
                            UserEntry.Action.PROFILE_SWITCH,
                            UserEntry.Sources.Automation,
                            rh.gs(info.nightscout.core.ui.R.string.autotune),
                            ValueWithUnit.SimpleString(tunedP.profileName)
                        )
                    }
                    rxBus.send(EventLocalProfileChanged())
                }
            }
        }

        tunedProfile?.let {
            saveLastRun()
            lastRunSuccess = true
            rxBus.send(EventAutotuneUpdateGui())
            calculationRunning = false
            return
        }
        result = rh.gs(info.nightscout.core.ui.R.string.autotune_error)
        rxBus.send(EventAutotuneUpdateGui())
        calculationRunning = false
        return
    }

    private fun showResults(tunedProfile: ATProfile?, pumpProfile: ATProfile): String {
        if (tunedProfile == null)
            return "No Result"  // should never occurs
        val line = rh.gs(info.nightscout.core.ui.R.string.autotune_log_separator)
        var strResult = line
        strResult += rh.gs(info.nightscout.core.ui.R.string.autotune_log_title)
        strResult += line
        val tuneInsulin = sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_tune_insulin_curve, false)
        if (tuneInsulin) {
            strResult += rh.gs(info.nightscout.core.ui.R.string.autotune_log_peak, rh.gs(R.string.insulin_peak), pumpProfile.localInsulin.peak, tunedProfile.localInsulin.peak)
            strResult += rh.gs(info.nightscout.core.ui.R.string.autotune_log_dia, rh.gs(info.nightscout.core.ui.R.string.ic_short), pumpProfile.localInsulin.dia, tunedProfile.localInsulin.dia)
        }
        // show ISF and CR
        strResult += rh.gs(info.nightscout.core.ui.R.string.autotune_log_ic_isf, rh.gs(info.nightscout.core.ui.R.string.isf_short), pumpProfile.isf, tunedProfile.isf)
        strResult += rh.gs(info.nightscout.core.ui.R.string.autotune_log_ic_isf, rh.gs(info.nightscout.core.ui.R.string.ic_short), pumpProfile.ic, tunedProfile.ic)
        strResult += line
        var totalBasal = 0.0
        var totalTuned = 0.0
        for (i in 0..23) {
            totalBasal += pumpProfile.basal[i]
            totalTuned += tunedProfile.basal[i]
            val percentageChangeValue = tunedProfile.basal[i] / pumpProfile.basal[i] * 100 - 100
            strResult += rh.gs(info.nightscout.core.ui.R.string.autotune_log_basal, i.toDouble(), pumpProfile.basal[i], tunedProfile.basal[i], tunedProfile.basalUnTuned[i], percentageChangeValue)
        }
        strResult += line
        strResult += rh.gs(info.nightscout.core.ui.R.string.autotune_log_sum_basal, totalBasal, totalTuned)
        strResult += line
        log(strResult)
        return strResult
    }

    private fun settings(runDate: Long, nbDays: Int, firstloopstart: Long, lastloopend: Long): String {
        var jsonString = ""
        val jsonSettings = JSONObject()
        val insulinInterface = activePlugin.activeInsulin
        val utcOffset = T.msecs(TimeZone.getDefault().getOffset(dateUtil.now()).toLong()).hours()
        val startDateString = dateUtil.toISOString(firstloopstart).substring(0, 10)
        val endDateString = dateUtil.toISOString(lastloopend - 24 * 60 * 60 * 1000L).substring(0, 10)
        val nsUrl = sp.getString(info.nightscout.core.utils.R.string.key_nsclientinternal_url, "")
        val optCategorizeUam = if (sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_categorize_uam_as_basal, false)) "-c=true" else ""
        val optInsulinCurve = if (sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_tune_insulin_curve, false)) "-i=true" else ""
        try {
            jsonSettings.put("datestring", dateUtil.toISOString(runDate))
            jsonSettings.put("dateutc", dateUtil.toISOAsUTC(runDate))
            jsonSettings.put("utcOffset", utcOffset)
            jsonSettings.put("units", profileFunction.getUnits().asText)
            jsonSettings.put("timezone", TimeZone.getDefault().id)
            jsonSettings.put("url_nightscout", sp.getString(info.nightscout.core.utils.R.string.key_nsclientinternal_url, ""))
            jsonSettings.put("nbdays", nbDays)
            jsonSettings.put("startdate", startDateString)
            jsonSettings.put("enddate", endDateString)
            // command to change timezone
            jsonSettings.put("timezone_command", "sudo ln -sf /usr/share/zoneinfo/" + TimeZone.getDefault().id + " /etc/localtime")
            // oref0_command is for running oref0-autotune on a virtual machine in a dedicated ~/aaps subfolder
            jsonSettings.put("oref0_command", "oref0-autotune -d=~/aaps -n=$nsUrl -s=$startDateString -e=$endDateString $optCategorizeUam $optInsulinCurve")
            // aaps_command is for running modified oref0-autotune with exported data from aaps (ns-entries and ns-treatment json files copied in ~/aaps/autotune folder and pumpprofile.json copied in ~/aaps/settings/
            jsonSettings.put("aaps_command", "aaps-autotune -d=~/aaps -s=$startDateString -e=$endDateString $optCategorizeUam $optInsulinCurve")
            jsonSettings.put("categorize_uam_as_basal", sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_categorize_uam_as_basal, false))
            jsonSettings.put("tune_insulin_curve", false)

            val peaktime: Int = insulinInterface.peak
            if (insulinInterface.id === Insulin.InsulinType.OREF_ULTRA_RAPID_ACTING)
                jsonSettings.put("curve", "ultra-rapid")
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
        } catch (e: JSONException) {
            aapsLogger.error(LTag.AUTOTUNE, e.localizedMessage ?: e.toString())
        }
        return jsonString
    }

    fun updateProfile(newProfile: ATProfile?) {
        if (newProfile == null) return
        val profilePlugin = activePlugin.activeProfileSource
        val circadian = sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_circadian_ic_isf, false)
        val profileStore = activePlugin.activeProfileSource.profile ?: instantiator.provideProfileStore(JSONObject())
        val profileList: ArrayList<CharSequence> = profileStore.getProfileList()
        var indexLocalProfile = -1
        for (p in profileList.indices)
            if (profileList[p] == newProfile.profileName)
                indexLocalProfile = p
        if (indexLocalProfile == -1) {
            profilePlugin.addProfile(profilePlugin.copyFrom(newProfile.getProfile(circadian), newProfile.profileName))
            return
        }
        profilePlugin.currentProfileIndex = indexLocalProfile
        profilePlugin.currentProfile()?.dia = newProfile.dia
        profilePlugin.currentProfile()?.basal = newProfile.basal()
        profilePlugin.currentProfile()?.ic = newProfile.ic(circadian)
        profilePlugin.currentProfile()?.isf = newProfile.isf(circadian)
        profilePlugin.storeSettings()
    }

    fun saveLastRun() {
        val json = JSONObject()
        json.put("lastNbDays", lastNbDays)
        json.put("lastRun", lastRun)
        json.put("pumpProfile", pumpProfile.profile.toPureNsJson(dateUtil))
        json.put("pumpProfileName", pumpProfile.profileName)
        json.put("pumpPeak", pumpProfile.peak)
        json.put("pumpDia", pumpProfile.dia)
        tunedProfile?.let { atProfile ->
            json.put("tunedProfile", atProfile.profile.toPureNsJson(dateUtil))
            json.put("tunedCircadianProfile", atProfile.circadianProfile.toPureNsJson(dateUtil))
            json.put("tunedProfileName", atProfile.profileName)
            json.put("tunedPeak", atProfile.peak)
            json.put("tunedDia", atProfile.dia)
            for (i in 0..23) {
                json.put("missingDays_$i", atProfile.basalUnTuned[i])
            }
        }
        for (i in days.weekdays.indices) {
            json.put(InputWeekDay.DayOfWeek.values()[i].name, days.weekdays[i])
        }
        json.put("result", result)
        json.put("updateButtonVisibility", updateButtonVisibility)
        sp.putString(info.nightscout.core.utils.R.string.key_autotune_last_run, json.toString())
    }

    fun loadLastRun() {
        result = ""
        lastRunSuccess = false
        try {
            val json = JSONObject(sp.getString(info.nightscout.core.utils.R.string.key_autotune_last_run, ""))
            lastNbDays = JsonHelper.safeGetString(json, "lastNbDays", "")
            lastRun = JsonHelper.safeGetLong(json, "lastRun")
            val pumpPeak = JsonHelper.safeGetInt(json, "pumpPeak")
            val pumpDia = JsonHelper.safeGetDouble(json, "pumpDia")
            var localInsulin = LocalInsulin("PumpInsulin", pumpPeak, pumpDia)
            selectedProfile = JsonHelper.safeGetString(json, "pumpProfileName", "")
            val profile = JsonHelper.safeGetJSONObject(json, "pumpProfile", null)?.let { pureProfileFromJson(it, dateUtil) }
                ?: return
            pumpProfile = ATProfile(ProfileSealed.Pure(profile), localInsulin, injector).also { it.profileName = selectedProfile }
            val tunedPeak = JsonHelper.safeGetInt(json, "tunedPeak")
            val tunedDia = JsonHelper.safeGetDouble(json, "tunedDia")
            localInsulin = LocalInsulin("PumpInsulin", tunedPeak, tunedDia)
            val tunedProfileName = JsonHelper.safeGetString(json, "tunedProfileName", "")
            val tuned = JsonHelper.safeGetJSONObject(json, "tunedProfile", null)?.let { pureProfileFromJson(it, dateUtil) }
                ?: return
            val circadianTuned = JsonHelper.safeGetJSONObject(json, "tunedCircadianProfile", null)?.let { pureProfileFromJson(it, dateUtil) }
                ?: return
            tunedProfile = ATProfile(ProfileSealed.Pure(tuned), localInsulin, injector).also { atProfile ->
                atProfile.profileName = tunedProfileName
                atProfile.circadianProfile = ProfileSealed.Pure(circadianTuned)
                for (i in 0..23) {
                    atProfile.basalUnTuned[i] = JsonHelper.safeGetInt(json, "missingDays_$i")
                }
            }
            for (i in days.weekdays.indices)
                days.weekdays[i] = JsonHelper.safeGetBoolean(json, InputWeekDay.DayOfWeek.values()[i].name,true)
            result = JsonHelper.safeGetString(json, "result", "")
            updateButtonVisibility = JsonHelper.safeGetInt(json, "updateButtonVisibility")
            lastRunSuccess = true
        } catch (e: Exception) {
            aapsLogger.error(LTag.AUTOTUNE, e.localizedMessage ?: e.toString())
        }
    }

    fun calcDays(daysBack:Int): Int {
            var endTime = MidnightTime.calc(dateUtil.now()) + autotuneStartHour * 60 * 60 * 1000L
            if (endTime > dateUtil.now()) endTime -= T.days(1).msecs()      // Check if 4 AM is before now
            val starttime = endTime - daysBack * T.days(1).msecs()
            var result = 0
            for (i in 0 until daysBack) {
                if (days.isSet(starttime + i * T.days(1).msecs()))
                    result++
            }
            return result
        }

    private fun log(message: String) {
        atLog("[Plugin] $message")
    }

    override fun specialEnableCondition(): Boolean = config.isEngineeringMode() && config.isDev()

    override fun atLog(message: String) {
        autotuneFS.atLog(message)
    }
}