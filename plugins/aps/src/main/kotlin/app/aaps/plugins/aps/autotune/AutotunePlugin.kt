@file:Suppress("SpellCheckingInspection")

package app.aaps.plugins.aps.autotune

import android.content.Context
import android.view.View
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLocalProfileChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.elements.WeekDay
import app.aaps.core.utils.JsonHelper
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.plugins.aps.autotune.data.LocalInsulin
import app.aaps.plugins.aps.autotune.data.PreppedGlucose
import app.aaps.plugins.aps.autotune.events.EventAutotuneUpdateGui
import app.aaps.plugins.aps.autotune.keys.AutotuneStringKey
import org.json.JSONException
import org.json.JSONObject
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/*
 * adaptation from oref0 autotune developed by philoul on 2022 (complete refactoring of AutotunePlugin initialised by Rumen Georgiev on 1/29/2018.)
 *
 * TODO: replace Thread by Worker
 */

@Singleton
class AutotunePlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
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
    private val profileStoreProvider: Provider<ProfileStore>,
    private val atProfileProvider: Provider<ATProfile>
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(AutotuneFragment::class.qualifiedName)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_autotune)
        .pluginName(app.aaps.core.ui.R.string.autotune)
        .shortName(R.string.autotune_shortname)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .showInList { config.isEngineeringMode() && config.isDev() || config.enableAutotune() }
        .description(R.string.autotune_description),
    ownPreferences = listOf(AutotuneStringKey::class.java),
    aapsLogger, rh, preferences
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
    val days = WeekDay()
    val autotuneStartHour: Int = 4

    override fun specialEnableCondition(): Boolean = config.isEngineeringMode() && config.isDev() || config.enableAutotune()

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
        for ((counter, i) in days.getSelectedDays().withIndex()) {
            if (counter > 0) sb.append(",")
            sb.append(WeekDay.DayOfWeek.fromCalendarInt(i))
        }
        log(sb.toString())
        tunedProfile = null
        updateButtonVisibility = View.GONE
        var logResult = ""
        result = ""
        if (profileFunction.getProfile() == null) {
            result = rh.gs(app.aaps.core.ui.R.string.profileswitch_ismissing)
            rxBus.send(EventAutotuneUpdateGui())
            calculationRunning = false
            return
        }
        val detailedLog = preferences.get(BooleanKey.AutotuneAdditionalLog)
        calculationRunning = true
        lastNbDays = "" + daysBack
        lastRun = dateUtil.now()
        val profileStore = activePlugin.activeProfileSource.profile
        if (profileStore == null) {
            result = rh.gs(app.aaps.core.ui.R.string.profileswitch_ismissing)
            rxBus.send(EventAutotuneUpdateGui())
            calculationRunning = false
            return
        }
        selectedProfile = profileToTune.ifEmpty { profileFunction.getProfileName() }
        profileFunction.getProfile()?.let { currentProfile ->
            profile = profileStore.getSpecificProfile(profileToTune)?.let { ProfileSealed.Pure(value = it, activePlugin = null) } ?: currentProfile
        }
        val localInsulin = LocalInsulin("PumpInsulin", activePlugin.activeInsulin.peak, profile.dia) // var because localInsulin could be updated later with Tune Insulin peak/dia

        log("Start Autotune with $daysBack days back")
        autotuneFS.createAutotuneFolder()                           //create autotune subfolder for autotune files if not exists
        autotuneFS.deleteAutotuneFiles()                            //clean autotune folder before run
        // Today at 4 AM
        var endTime = MidnightTime.calc(lastRun) + autotuneStartHour * 60 * 60 * 1000L
        if (endTime > lastRun) endTime -= 24 * 60 * 60 * 1000L      // Check if 4 AM is before now
        val startTime = endTime - daysBack * 24 * 60 * 60 * 1000L
        autotuneFS.exportSettings(settings(lastRun, daysBack, startTime, endTime))
        tunedProfile = atProfileProvider.get().with(profile, localInsulin).also {
            it.profileName = rh.gs(R.string.autotune_tunedprofile_name)
        }
        pumpProfile = atProfileProvider.get().with(profile, localInsulin).also {
            it.profileName = selectedProfile
        }
        autotuneFS.exportPumpProfile(pumpProfile)

        if (calcDays == 0) {
            result = rh.gs(R.string.autotune_error_more_days)
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
            val from = startTime + i * 24 * 60 * 60 * 1000L         // get 24 hours BG values from 4 AM to 4 AM next day
            val to = from + 24 * 60 * 60 * 1000L
            if (days.isSet(from)) {
                currentCalcDay++

                log("Tune day " + (i + 1) + " of " + daysBack + " (" + currentCalcDay + " of " + calcDays + ")")
                tunedProfile?.let {
                    autotuneIob.initializeData(from, to, it)  //autotuneIob contains BG and Treatments data from history (<=> query for ns-treatments and ns-entries)
                    if (autotuneIob.boluses.isEmpty()) {
                        result = rh.gs(R.string.autotune_error)
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
                                result = rh.gs(R.string.autotune_partial_result, currentCalcDay, calcDays)
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
                    result = rh.gs(R.string.autotune_error)
                    log("TunedProfile is null on day ${i + 1}")
                    autotuneFS.exportResult(result)
                    autotuneFS.exportLogAndZip(lastRun)
                    rxBus.send(EventAutotuneUpdateGui())
                    calculationRunning = false
                    return
                }
            }
        }
        result = rh.gs(R.string.autotune_result, dateUtil.dateAndTimeString(lastRun))
        if (!detailedLog)
            autotuneFS.exportLog(lastRun)
        autotuneFS.exportResult(logResult)
        autotuneFS.zipAutotune(lastRun)
        updateButtonVisibility = View.VISIBLE

        if (autoSwitch) {
            val circadian = preferences.get(BooleanKey.AutotuneCircadianIcIsf)
            tunedProfile?.let { tunedP ->
                tunedP.profileName = pumpProfile.profileName
                updateProfile(tunedP)
                uel.log(
                    action = Action.STORE_PROFILE,
                    source = Sources.Automation,
                    note = rh.gs(app.aaps.core.ui.R.string.autotune),
                    value = ValueWithUnit.SimpleString(tunedP.profileName)
                )
                updateButtonVisibility = View.GONE
                tunedP.profileStore(circadian)?.let { profileStore ->
                    if (profileFunction.createProfileSwitch(
                            profileStore = profileStore,
                            profileName = tunedP.profileName,
                            durationInMinutes = 0,
                            percentage = 100,
                            timeShiftInHours = 0,
                            timestamp = dateUtil.now(),
                            action = Action.PROFILE_SWITCH,
                            source = Sources.Automation,
                            note = rh.gs(app.aaps.core.ui.R.string.autotune),
                            listValues = listOf(ValueWithUnit.SimpleString(tunedP.profileName))
                        )
                    ) log("Profile Switch succeed ${tunedP.profileName}")
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
        result = rh.gs(R.string.autotune_error)
        rxBus.send(EventAutotuneUpdateGui())
        calculationRunning = false
        return
    }

    private fun showResults(tunedProfile: ATProfile?, pumpProfile: ATProfile): String {
        if (tunedProfile == null)
            return "No Result"  // should never occurs
        val line = rh.gs(R.string.autotune_log_separator)
        var strResult = line
        strResult += rh.gs(R.string.autotune_log_title)
        strResult += line
        val tuneInsulin = preferences.get(BooleanKey.AutotuneTuneInsulinCurve)
        if (tuneInsulin) {
            strResult += rh.gs(R.string.autotune_log_peak, rh.gs(R.string.insulin_peak), pumpProfile.localInsulin.peak, tunedProfile.localInsulin.peak)
            strResult += rh.gs(R.string.autotune_log_dia, rh.gs(app.aaps.core.ui.R.string.ic_short), pumpProfile.localInsulin.dia, tunedProfile.localInsulin.dia)
        }
        // show ISF and CR
        strResult += rh.gs(R.string.autotune_log_ic_isf, rh.gs(app.aaps.core.ui.R.string.isf_short), pumpProfile.isf, tunedProfile.isf)
        strResult += rh.gs(R.string.autotune_log_ic_isf, rh.gs(app.aaps.core.ui.R.string.ic_short), pumpProfile.ic, tunedProfile.ic)
        strResult += line
        var totalBasal = 0.0
        var totalTuned = 0.0
        for (i in 0..23) {
            totalBasal += pumpProfile.basal[i]
            totalTuned += tunedProfile.basal[i]
            val percentageChangeValue = tunedProfile.basal[i] / pumpProfile.basal[i] * 100 - 100
            strResult += rh.gs(R.string.autotune_log_basal, i.toDouble(), pumpProfile.basal[i], tunedProfile.basal[i], tunedProfile.basalUnTuned[i], percentageChangeValue)
        }
        strResult += line
        strResult += rh.gs(R.string.autotune_log_sum_basal, totalBasal, totalTuned)
        strResult += line
        log(strResult)
        return strResult
    }

    private fun settings(runDate: Long, nbDays: Int, firstLoopStart: Long, lastLoopEnd: Long): String {
        var jsonString = ""
        val jsonSettings = JSONObject()
        val insulinInterface = activePlugin.activeInsulin
        val utcOffset = T.msecs(TimeZone.getDefault().getOffset(dateUtil.now()).toLong()).hours()
        val startDateString = dateUtil.toISOString(firstLoopStart).substring(0, 10)
        val endDateString = dateUtil.toISOString(lastLoopEnd - 24 * 60 * 60 * 1000L).substring(0, 10)
        val nsUrl = preferences.get(StringKey.NsClientUrl)
        val optCategorizeUam = if (preferences.get(BooleanKey.AutotuneCategorizeUamAsBasal)) "-c=true" else ""
        val optInsulinCurve = if (preferences.get(BooleanKey.AutotuneTuneInsulinCurve)) "-i=true" else ""
        try {
            jsonSettings.put("datestring", dateUtil.toISOString(runDate))
            jsonSettings.put("dateutc", dateUtil.toISOAsUTC(runDate))
            jsonSettings.put("utcOffset", utcOffset)
            jsonSettings.put("units", profileFunction.getUnits().asText)
            jsonSettings.put("timezone", TimeZone.getDefault().id)
            jsonSettings.put("url_nightscout", nsUrl)
            jsonSettings.put("nbdays", nbDays)
            jsonSettings.put("startdate", startDateString)
            jsonSettings.put("enddate", endDateString)
            // command to change timezone
            jsonSettings.put("timezone_command", "sudo ln -sf /usr/share/zoneinfo/" + TimeZone.getDefault().id + " /etc/localtime")
            // oref0_command is for running oref0-autotune on a virtual machine in a dedicated ~/aaps subfolder
            jsonSettings.put("oref0_command", "oref0-autotune -d=~/aaps -n=$nsUrl -s=$startDateString -e=$endDateString $optCategorizeUam $optInsulinCurve")
            // aaps_command is for running modified oref0-autotune with exported data from aaps (ns-entries and ns-treatment json files copied in ~/aaps/autotune folder and pumpprofile.json copied in ~/aaps/settings/
            jsonSettings.put("aaps_command", "aaps-autotune -d=~/aaps -s=$startDateString -e=$endDateString $optCategorizeUam $optInsulinCurve")
            jsonSettings.put("categorize_uam_as_basal", preferences.get(BooleanKey.AutotuneCategorizeUamAsBasal))
            jsonSettings.put("tune_insulin_curve", false)

            val peakTime: Int = insulinInterface.peak
            when {
                insulinInterface.id === Insulin.InsulinType.OREF_ULTRA_RAPID_ACTING -> jsonSettings.put("curve", "ultra-rapid")
                insulinInterface.id === Insulin.InsulinType.OREF_RAPID_ACTING       -> jsonSettings.put("curve", "rapid-acting")

                insulinInterface.id === Insulin.InsulinType.OREF_LYUMJEV            -> {
                    jsonSettings.put("curve", "ultra-rapid")
                    jsonSettings.put("useCustomPeakTime", true)
                    jsonSettings.put("insulinPeakTime", peakTime)
                }

                insulinInterface.id === Insulin.InsulinType.OREF_FREE_PEAK          -> {
                    jsonSettings.put("curve", if (peakTime > 55) "rapid-acting" else "ultra-rapid")
                    jsonSettings.put("useCustomPeakTime", true)
                    jsonSettings.put("insulinPeakTime", peakTime)
                }
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
        val circadian = preferences.get(BooleanKey.AutotuneCircadianIcIsf)
        val profileStore = activePlugin.activeProfileSource.profile ?: profileStoreProvider.get().with(JSONObject())
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
        profilePlugin.storeSettings(timestamp = dateUtil.now())
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
            json.put(WeekDay.DayOfWeek.entries[i].name, days.weekdays[i])
        }
        json.put("result", result)
        json.put("updateButtonVisibility", updateButtonVisibility)
        preferences.put(AutotuneStringKey.AutotuneLastRun, json.toString())
    }

    fun loadLastRun() {
        result = ""
        lastRunSuccess = false
        try {
            val json = JSONObject(preferences.get(AutotuneStringKey.AutotuneLastRun))
            lastNbDays = JsonHelper.safeGetString(json, "lastNbDays", "")
            lastRun = JsonHelper.safeGetLong(json, "lastRun")
            val pumpPeak = JsonHelper.safeGetInt(json, "pumpPeak")
            val pumpDia = JsonHelper.safeGetDouble(json, "pumpDia")
            var localInsulin = LocalInsulin("PumpInsulin", pumpPeak, pumpDia)
            selectedProfile = JsonHelper.safeGetString(json, "pumpProfileName", "")
            val profile = JsonHelper.safeGetJSONObject(json, "pumpProfile", null)?.let { pureProfileFromJson(it, dateUtil) }
                ?: return
            pumpProfile = atProfileProvider.get().with(ProfileSealed.Pure(value = profile, activePlugin = null), localInsulin).also { it.profileName = selectedProfile }
            val tunedPeak = JsonHelper.safeGetInt(json, "tunedPeak")
            val tunedDia = JsonHelper.safeGetDouble(json, "tunedDia")
            localInsulin = LocalInsulin("PumpInsulin", tunedPeak, tunedDia)
            val tunedProfileName = JsonHelper.safeGetString(json, "tunedProfileName", "")
            val tuned = JsonHelper.safeGetJSONObject(json, "tunedProfile", null)?.let { pureProfileFromJson(it, dateUtil) }
                ?: return
            val circadianTuned = JsonHelper.safeGetJSONObject(json, "tunedCircadianProfile", null)?.let { pureProfileFromJson(it, dateUtil) }
                ?: return
            tunedProfile = atProfileProvider.get().with(ProfileSealed.Pure(value = tuned, activePlugin = null), localInsulin).also { atProfile ->
                atProfile.profileName = tunedProfileName
                atProfile.circadianProfile = ProfileSealed.Pure(value = circadianTuned, activePlugin = null)
                for (i in 0..23) {
                    atProfile.basalUnTuned[i] = JsonHelper.safeGetInt(json, "missingDays_$i")
                }
            }
            for (i in days.weekdays.indices)
                days.weekdays[i] = JsonHelper.safeGetBoolean(json, WeekDay.DayOfWeek.entries[i].name, true)
            result = JsonHelper.safeGetString(json, "result", "")
            updateButtonVisibility = JsonHelper.safeGetInt(json, "updateButtonVisibility")
            lastRunSuccess = true
        } catch (e: Exception) {
            aapsLogger.error(LTag.AUTOTUNE, e.localizedMessage ?: e.toString())
        }
    }

    fun calcDays(daysBack: Int): Int {
        var endTime = MidnightTime.calc(dateUtil.now()) + autotuneStartHour * 60 * 60 * 1000L
        if (endTime > dateUtil.now()) endTime = MidnightTime.calcDaysBack(1)      // Check if 4 AM is before now
        val startTime = MidnightTime.calcDaysBack(endTime, daysBack.toLong())
        var result = 0
        for (i in 0 until daysBack) {
            if (days.isSet(startTime + i * T.days(1).msecs()))
                result++
        }
        return result
    }

    private fun log(message: String) {
        atLog("[Plugin] $message")
    }

    override fun atLog(message: String) {
        autotuneFS.atLog(message)
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return
        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "autotune_settings"
            title = rh.gs(R.string.autotune_settings)
            initialExpandedChildrenCount = 0
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.AutotuneAutoSwitchProfile, summary = R.string.autotune_auto_summary, title = R.string.autotune_auto_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.AutotuneCategorizeUamAsBasal, summary = R.string.autotune_categorize_uam_as_basal_summary, title = R.string.autotune_categorize_uam_as_basal_title))
            //addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.AutotuneTuneInsulinCurve, summary = R.string.autotune_tune_insulin_curve_summary, title = R.string.autotune_tune_insulin_curve_title))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = IntKey.AutotuneDefaultTuneDays, dialogMessage = R.string.autotune_default_tune_days_summary, title = R.string.autotune_default_tune_days_title))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.AutotuneCircadianIcIsf, summary = R.string.autotune_circadian_ic_isf_summary, title = R.string.autotune_circadian_ic_isf_title))
            //addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = BooleanKey.AutotuneAdditionalLog, summary = R.string.autotune_additional_log_summary, title = R.string.autotune_additional_log_title))
        }
    }
}