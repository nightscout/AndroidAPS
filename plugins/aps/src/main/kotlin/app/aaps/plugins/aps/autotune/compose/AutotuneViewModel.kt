package app.aaps.plugins.aps.autotune.compose

import android.os.Handler
import android.os.HandlerThread
import android.view.View
import androidx.compose.runtime.Immutable
import app.aaps.core.graph.profile.ProfileViewerData
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLocalProfileChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.elements.WeekDay
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.autotune.AutotuneFS
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.plugins.aps.autotune.data.LocalInsulin
import app.aaps.plugins.aps.autotune.events.EventAutotuneUpdateGui
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.Locale
import javax.inject.Provider

@Immutable
data class ResultRow(
    val label: String,
    val profileValue: String,
    val tunedValue: String,
    val percent: String,
    val missing: String = ""
)

@Immutable
data class AutotuneUiState(
    val profileList: List<String> = emptyList(),
    val selectedProfileIndex: Int = 0,
    val daysBack: Double = 5.0,
    val calcDays: Int = 0,
    val weekdays: BooleanArray = BooleanArray(7) { true },
    val showWeekDays: Boolean = false,
    val lastRunText: String = "",
    val warningText: String = "",
    val resultText: String = "",
    val isRunning: Boolean = false,
    val lastRunSuccess: Boolean = false,
    val showRun: Boolean = false,
    val showCheckInput: Boolean = false,
    val showCopyLocal: Boolean = false,
    val showUpdateProfile: Boolean = false,
    val showRevertProfile: Boolean = false,
    val showProfileSwitch: Boolean = false,
    val showCompare: Boolean = false,
    val showResultsCard: Boolean = true,
    val showCalcDays: Boolean = false,
    val paramRows: List<ResultRow> = emptyList(),
    val basalRows: List<ResultRow> = emptyList(),
    val dialogState: DialogState = DialogState.None
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AutotuneUiState) return false
        return profileList == other.profileList &&
            selectedProfileIndex == other.selectedProfileIndex &&
            daysBack == other.daysBack &&
            calcDays == other.calcDays &&
            weekdays.contentEquals(other.weekdays) &&
            showWeekDays == other.showWeekDays &&
            lastRunText == other.lastRunText &&
            warningText == other.warningText &&
            resultText == other.resultText &&
            isRunning == other.isRunning &&
            lastRunSuccess == other.lastRunSuccess &&
            showRun == other.showRun &&
            showCheckInput == other.showCheckInput &&
            showCopyLocal == other.showCopyLocal &&
            showUpdateProfile == other.showUpdateProfile &&
            showRevertProfile == other.showRevertProfile &&
            showProfileSwitch == other.showProfileSwitch &&
            showCompare == other.showCompare &&
            showResultsCard == other.showResultsCard &&
            showCalcDays == other.showCalcDays &&
            paramRows == other.paramRows &&
            basalRows == other.basalRows &&
            dialogState == other.dialogState
    }

    override fun hashCode(): Int {
        var result = profileList.hashCode()
        result = 31 * result + selectedProfileIndex
        result = 31 * result + daysBack.hashCode()
        result = 31 * result + calcDays
        result = 31 * result + weekdays.contentHashCode()
        result = 31 * result + showWeekDays.hashCode()
        result = 31 * result + lastRunText.hashCode()
        result = 31 * result + warningText.hashCode()
        result = 31 * result + resultText.hashCode()
        result = 31 * result + isRunning.hashCode()
        result = 31 * result + lastRunSuccess.hashCode()
        result = 31 * result + showRun.hashCode()
        result = 31 * result + showCheckInput.hashCode()
        result = 31 * result + showCopyLocal.hashCode()
        result = 31 * result + showUpdateProfile.hashCode()
        result = 31 * result + showRevertProfile.hashCode()
        result = 31 * result + showProfileSwitch.hashCode()
        result = 31 * result + showCompare.hashCode()
        result = 31 * result + showResultsCard.hashCode()
        result = 31 * result + showCalcDays.hashCode()
        result = 31 * result + paramRows.hashCode()
        result = 31 * result + basalRows.hashCode()
        result = 31 * result + dialogState.hashCode()
        return result
    }
}

sealed class DialogState {
    data object None : DialogState()
    data class CopyLocal(val localName: String) : DialogState()
    data class UpdateProfile(val profileName: String) : DialogState()
    data class RevertProfile(val profileName: String) : DialogState()
    data class ProfileSwitch(val profileName: String) : DialogState()
    data class PumpDisconnected(val title: String) : DialogState()
    data class ShowProfileViewer(val data: ProfileViewerData) : DialogState()
}

class AutotuneViewModel(
    private val autotunePlugin: AutotunePlugin,
    private val autotuneFS: AutotuneFS,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val localProfileManager: LocalProfileManager,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val uel: UserEntryLogger,
    private val loop: Loop,
    private val insulin: Insulin,
    private val profileStoreProvider: Provider<ProfileStore>,
    private val atProfileProvider: Provider<ATProfile>,
    private val scope: CoroutineScope
) {

    private val _uiState = MutableStateFlow(AutotuneUiState())
    val uiState: StateFlow<AutotuneUiState> = _uiState.asStateFlow()

    private val days get() = autotunePlugin.days
    private val handler = Handler(HandlerThread("AutotuneCompose").also { it.start() }.looper)

    private var profileName = ""
    private var profile: ATProfile? = null

    init {
        preferences.put(BooleanKey.AutotuneTuneInsulinCurve, false)
        preferences.put(BooleanKey.AutotuneAdditionalLog, false)
        autotunePlugin.loadLastRun()
        if (autotunePlugin.lastNbDays.isEmpty())
            autotunePlugin.lastNbDays = preferences.get(IntKey.AutotuneDefaultTuneDays).toString()

        rxBus.toFlow(EventAutotuneUpdateGui::class.java)
            .onEach { refreshState() }
            .launchIn(scope)

        checkNewDay()
        scope.launch { refreshState() }
    }

    fun onProfileSelected(index: Int) {
        if (autotunePlugin.calculationRunning) {
            scope.launch { refreshState() }
            return
        }
        val state = _uiState.value
        val selectedName = if (index == 0) "" else state.profileList.getOrElse(index) { "" }
        profileName = selectedName
        autotunePlugin.selectedProfile = selectedName
        scope.launch {
            resolveProfile()
            resetParam(true)
            refreshState()
        }
    }

    fun onDaysChanged(newDays: Double) {
        if (autotunePlugin.calculationRunning) return
        val intDays = newDays.toInt().coerceIn(1, 30)
        autotunePlugin.lastNbDays = intDays.toString()
        resetParam(false)
        scope.launch { refreshState() }
    }

    fun onDayToggle(day: WeekDay.DayOfWeek, selected: Boolean) {
        if (autotunePlugin.calculationRunning) return
        days[day] = selected
        resetParam(false)
        scope.launch { refreshState() }
    }

    fun onToggleWeekDays() {
        val current = _uiState.value.showWeekDays
        _uiState.value = _uiState.value.copy(showWeekDays = !current)
    }

    fun onRunAutotune() {
        val daysBack = autotunePlugin.lastNbDays.toIntOrNull() ?: return
        log("Run Autotune $profileName, $daysBack days")
        handler.post { autotunePlugin.aapsAutotune(daysBack, false, profileName) }
        scope.launch { refreshState() }
    }

    fun onLoadLastRun() {
        if (autotunePlugin.calculationRunning) return
        autotunePlugin.loadLastRun()
        scope.launch { refreshState() }
    }

    // --- Dialog actions ---

    fun onCopyLocalClick() {
        val localName = rh.gs(R.string.autotune_tunedprofile_name) + " " + dateUtil.dateAndTimeString(autotunePlugin.lastRun)
        _uiState.value = _uiState.value.copy(dialogState = DialogState.CopyLocal(localName))
    }

    fun onCopyLocalConfirm(localName: String) {
        val circadian = preferences.get(BooleanKey.AutotuneCircadianIcIsf)
        autotunePlugin.tunedProfile?.let { tunedProfile ->
            localProfileManager.addProfile(localProfileManager.copyFrom(tunedProfile.getProfile(circadian), localName))
            uel.log(action = Action.NEW_PROFILE, source = Sources.Autotune, value = ValueWithUnit.SimpleString(localName))
        }
        _uiState.value = _uiState.value.copy(dialogState = DialogState.None)
        scope.launch { refreshState() }
    }

    fun onUpdateProfileClick() {
        _uiState.value = _uiState.value.copy(dialogState = DialogState.UpdateProfile(autotunePlugin.pumpProfile.profileName))
    }

    fun onUpdateProfileConfirm() {
        val localName = autotunePlugin.pumpProfile.profileName
        autotunePlugin.tunedProfile?.profileName = localName
        autotunePlugin.updateProfile(autotunePlugin.tunedProfile)
        autotunePlugin.updateButtonVisibility = View.GONE
        autotunePlugin.saveLastRun()
        uel.log(action = Action.STORE_PROFILE, source = Sources.Autotune, value = ValueWithUnit.SimpleString(localName))
        _uiState.value = _uiState.value.copy(dialogState = DialogState.None)
        scope.launch { refreshState() }
    }

    fun onRevertProfileClick() {
        _uiState.value = _uiState.value.copy(dialogState = DialogState.RevertProfile(autotunePlugin.pumpProfile.profileName))
    }

    fun onRevertProfileConfirm() {
        val localName = autotunePlugin.pumpProfile.profileName
        autotunePlugin.tunedProfile?.profileName = ""
        autotunePlugin.updateProfile(autotunePlugin.pumpProfile)
        autotunePlugin.updateButtonVisibility = View.VISIBLE
        autotunePlugin.saveLastRun()
        uel.log(action = Action.STORE_PROFILE, source = Sources.Autotune, value = ValueWithUnit.SimpleString(localName))
        _uiState.value = _uiState.value.copy(dialogState = DialogState.None)
        scope.launch { refreshState() }
    }

    fun onProfileSwitchClick() {
        val tunedProfile = autotunePlugin.tunedProfile ?: return
        autotunePlugin.updateProfile(tunedProfile)
        if (loop.runningMode == RM.Mode.DISCONNECTED_PUMP) {
            _uiState.value = _uiState.value.copy(
                dialogState = DialogState.PumpDisconnected(rh.gs(app.aaps.core.ui.R.string.not_available_full))
            )
            return
        }
        _uiState.value = _uiState.value.copy(dialogState = DialogState.ProfileSwitch(tunedProfile.profileName))
    }

    fun onProfileSwitchConfirm() {
        val circadian = preferences.get(BooleanKey.AutotuneCircadianIcIsf)
        autotunePlugin.tunedProfile?.let { tunedP ->
            tunedP.profileStore(circadian)?.let { profileStore ->
                uel.log(action = Action.STORE_PROFILE, source = Sources.Autotune, value = ValueWithUnit.SimpleString(tunedP.profileName))
                val now = dateUtil.now()
                val iCfg = insulin.iCfg
                scope.launch {
                    profileFunction.createProfileSwitch(
                        profileStore = profileStore,
                        profileName = tunedP.profileName,
                        durationInMinutes = 0,
                        percentage = 100,
                        timeShiftInHours = 0,
                        timestamp = now,
                        action = Action.PROFILE_SWITCH,
                        source = Sources.Autotune,
                        note = "Autotune AutoSwitch",
                        listValues = listOf(ValueWithUnit.SimpleString(tunedP.profileName)),
                        iCfg = iCfg
                    )
                }
                rxBus.send(EventLocalProfileChanged())
            }
        }
        _uiState.value = _uiState.value.copy(dialogState = DialogState.None)
        scope.launch { refreshState() }
    }

    fun onCheckInputProfile() {
        scope.launch {
            val profileStore = localProfileManager.profile ?: profileStoreProvider.get().with(JSONObject())
            val pumpProfile = profileFunction.getProfile()?.let { currentProfile ->
                profileStore.getSpecificProfile(profileName)?.let { specificProfile ->
                    atProfileProvider.get().with(ProfileSealed.Pure(specificProfile, null), LocalInsulin("")).also {
                        it.profileName = profileName
                    }
                } ?: atProfileProvider.get().with(currentProfile, LocalInsulin("")).also {
                    it.profileName = profileFunction.getProfileName()
                }
            } ?: return@launch
            _uiState.value = _uiState.value.copy(
                dialogState = DialogState.ShowProfileViewer(
                    ProfileViewerData(
                        profile = pumpProfile.profile,
                        profileName = pumpProfile.profileName,
                        headerIcon = app.aaps.core.ui.R.drawable.ic_home_profile
                    )
                )
            )
        }
    }

    fun onCompareProfiles() {
        val pumpProfile = autotunePlugin.pumpProfile
        val circadian = preferences.get(BooleanKey.AutotuneCircadianIcIsf)
        val tunedProfile = if (circadian) autotunePlugin.tunedProfile?.circadianProfile else autotunePlugin.tunedProfile?.profile
        _uiState.value = _uiState.value.copy(
            dialogState = DialogState.ShowProfileViewer(
                ProfileViewerData(
                    profile = pumpProfile.profile,
                    profile2 = tunedProfile,
                    profileName = pumpProfile.profileName,
                    profileName2 = rh.gs(R.string.autotune_tunedprofile_name),
                    headerIcon = app.aaps.core.objects.R.drawable.ic_compare_profiles,
                    isCompare = true
                )
            )
        )
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(dialogState = DialogState.None)
    }

    fun onDispose() {
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    // --- Internal ---

    private suspend fun resolveProfile() {
        val profileStore = localProfileManager.profile ?: profileStoreProvider.get().with(JSONObject())
        profileFunction.getProfile()?.let { currentProfile ->
            profile = atProfileProvider.get().with(
                profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(value = it, activePlugin = null) } ?: currentProfile,
                LocalInsulin("")
            )
        }
    }

    private fun checkNewDay() {
        val runToday = autotunePlugin.lastRun > MidnightTime.calc(dateUtil.now() - autotunePlugin.autotuneStartHour * 3600 * 1000L) + autotunePlugin.autotuneStartHour * 3600 * 1000L
        if (!runToday || autotunePlugin.result.isEmpty())
            resetParam(!runToday)
    }

    private fun resetParam(resetDay: Boolean) {
        if (resetDay) {
            autotunePlugin.lastNbDays = preferences.get(IntKey.AutotuneDefaultTuneDays).toString()
            days.setAll(true)
        }
        autotunePlugin.result = ""
        autotunePlugin.tunedProfile = null
        autotunePlugin.lastRunSuccess = false
        autotunePlugin.updateButtonVisibility = View.GONE
    }

    private suspend fun addWarnings(): String {
        val currentProfile = profileFunction.getProfile() ?: return rh.gs(app.aaps.core.ui.R.string.profileswitch_ismissing)
        val profileStore = localProfileManager.profile ?: profileStoreProvider.get().with(JSONObject())
        val atProfile = atProfileProvider.get().with(
            profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(value = it, activePlugin = null) } ?: currentProfile,
            LocalInsulin("")
        )
        profile = atProfile
        if (!atProfile.isValid) return rh.gs(R.string.autotune_profile_invalid)
        val warnings = mutableListOf<String>()
        if (atProfile.icSize > 1)
            warnings += rh.gs(R.string.autotune_ic_warning, atProfile.icSize, atProfile.ic)
        if (atProfile.isfSize > 1)
            warnings += rh.gs(R.string.autotune_isf_warning, atProfile.isfSize, profileUtil.fromMgdlToUnits(atProfile.isf), profileFunction.getUnits().asText)
        return warnings.joinToString("\n")
    }

    private fun buildResultRows(): Pair<List<ResultRow>, List<ResultRow>> {
        val tunedProfile = autotunePlugin.tunedProfile ?: return Pair(emptyList(), emptyList())
        val pumpProfile = autotunePlugin.pumpProfile

        var toMgDl = 1.0
        if (profileFunction.getUnits() == GlucoseUnit.MMOL) toMgDl = Constants.MMOLL_TO_MGDL
        val isfFormat = if (profileFunction.getUnits() == GlucoseUnit.MMOL) "%.2f" else "%.1f"

        val params = mutableListOf<ResultRow>()
        val tuneInsulin = preferences.get(BooleanKey.AutotuneTuneInsulinCurve)
        if (tuneInsulin) {
            params += formatRow(rh.gs(R.string.insulin_peak), pumpProfile.localInsulin.peak.toDouble(), tunedProfile.localInsulin.peak.toDouble(), "%.0f")
            params += formatRow(rh.gs(app.aaps.core.ui.R.string.dia), Round.roundTo(pumpProfile.localInsulin.dia, 0.1), Round.roundTo(tunedProfile.localInsulin.dia, 0.1), "%.1f")
        }
        params += formatRow(rh.gs(app.aaps.core.ui.R.string.isf_short), Round.roundTo(pumpProfile.isf / toMgDl, 0.001), Round.roundTo(tunedProfile.isf / toMgDl, 0.001), isfFormat)
        params += formatRow(rh.gs(app.aaps.core.ui.R.string.ic_short), Round.roundTo(pumpProfile.ic, 0.001), Round.roundTo(tunedProfile.ic, 0.001), "%.2f")

        val basals = mutableListOf<ResultRow>()
        val df = DecimalFormat("00")
        var totalPump = 0.0
        var totalTuned = 0.0
        for (h in 0 until tunedProfile.basal.size) {
            val time = df.format(h.toLong()) + ":00"
            totalPump += pumpProfile.basal[h]
            totalTuned += tunedProfile.basal[h]
            basals += formatRow(time, pumpProfile.basal[h], tunedProfile.basal[h], "%.3f", tunedProfile.basalUnTuned[h].toString())
        }
        basals += formatRow("∑", totalPump, totalTuned, "%.3f", " ")

        return Pair(params, basals)
    }

    private fun formatRow(label: String, input: Double, tuned: Double, format: String, missing: String = ""): ResultRow {
        val percent = if (input != 0.0) Round.roundTo(tuned / input * 100 - 100, 1.0).toInt().toString() + "%" else ""
        return ResultRow(
            label = label,
            profileValue = String.format(Locale.getDefault(), format, input),
            tunedValue = String.format(Locale.getDefault(), format, tuned),
            percent = percent,
            missing = missing
        )
    }

    private suspend fun refreshState() {
        val profileStore = localProfileManager.profile ?: profileStoreProvider.get().with(JSONObject())
        val profileList = profileStore.getProfileList().toMutableList()
        profileList.add(0, rh.gs(app.aaps.core.ui.R.string.active))
        val profileNames = profileList.map { it.toString() }

        val selectedIndex = if (autotunePlugin.selectedProfile.isNotEmpty())
            profileNames.indexOf(autotunePlugin.selectedProfile).coerceAtLeast(0)
        else 0

        profileName = if (selectedIndex == 0) "" else profileNames.getOrElse(selectedIndex) { "" }
        resolveProfile()

        val daysBack = autotunePlugin.lastNbDays.toDoubleOrNull() ?: preferences.get(IntKey.AutotuneDefaultTuneDays).toDouble()
        val calcDays = autotunePlugin.calcDays(daysBack.toInt())
        val warning = if (autotunePlugin.calculationRunning) rh.gs(R.string.autotune_warning_during_run)
        else if (autotunePlugin.lastRunSuccess) rh.gs(R.string.autotune_warning_after_run)
        else addWarnings()

        val (paramRows, basalRows) = if (autotunePlugin.result.isNotBlank()) buildResultRows() else Pair(emptyList(), emptyList())

        _uiState.value = AutotuneUiState(
            profileList = profileNames,
            selectedProfileIndex = selectedIndex,
            daysBack = daysBack,
            calcDays = calcDays,
            weekdays = days.weekdays.copyOf(),
            showWeekDays = _uiState.value.showWeekDays,
            lastRunText = dateUtil.dateAndTimeString(autotunePlugin.lastRun),
            warningText = warning,
            resultText = autotunePlugin.result,
            isRunning = autotunePlugin.calculationRunning,
            lastRunSuccess = autotunePlugin.lastRunSuccess,
            showRun = !autotunePlugin.calculationRunning && !autotunePlugin.lastRunSuccess && profile?.isValid == true && calcDays > 0,
            showCheckInput = !autotunePlugin.calculationRunning && !autotunePlugin.lastRunSuccess,
            showCopyLocal = autotunePlugin.lastRunSuccess,
            showUpdateProfile = autotunePlugin.lastRunSuccess && autotunePlugin.updateButtonVisibility == View.VISIBLE,
            showRevertProfile = autotunePlugin.lastRunSuccess && autotunePlugin.updateButtonVisibility != View.VISIBLE,
            showProfileSwitch = autotunePlugin.lastRunSuccess,
            showCompare = autotunePlugin.lastRunSuccess,
            showResultsCard = !(autotunePlugin.calculationRunning && autotunePlugin.result.isEmpty()),
            showCalcDays = daysBack.toInt() != calcDays,
            paramRows = paramRows,
            basalRows = basalRows,
            dialogState = _uiState.value.dialogState
        )
    }

    private fun log(message: String) {
        autotuneFS.atLog("[Fragment] $message")
    }
}
