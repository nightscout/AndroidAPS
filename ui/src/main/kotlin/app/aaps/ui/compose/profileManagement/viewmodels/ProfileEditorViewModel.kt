package app.aaps.ui.compose.profileManagement.viewmodels

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileErrorType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLocalProfileChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.objects.profile.ProfileSealed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class TimeValue(
    val timeSeconds: Int,
    val value: Double
)

@Immutable
data class SingleProfileState(
    val name: String = "",
    val mgdl: Boolean = true,
    val dia: Double = 5.0,
    val ic: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val isf: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val basal: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val targetLow: List<TimeValue> = listOf(TimeValue(0, 0.0)),
    val targetHigh: List<TimeValue> = listOf(TimeValue(0, 0.0))
)

@Immutable
data class ProfileUiState(
    val profiles: List<String> = emptyList(),
    val currentProfileIndex: Int = 0,
    val currentProfile: SingleProfileState? = null,
    val isEdited: Boolean = false,
    val isValid: Boolean = true,
    val isLocked: Boolean = false,
    val selectedTab: Int = 0,
    val units: String = GlucoseUnit.MGDL.asText,
    val supportsDynamicIsf: Boolean = false,
    val supportsDynamicIc: Boolean = false,
    val basalMin: Double = 0.01,
    val basalMax: Double = 10.0,
    val icMin: Double = 0.5,
    val icMax: Double = 100.0,
    val isfMin: Double = 2.0,
    val isfMax: Double = 1000.0,
    val targetMin: Double = 72.0,
    val targetMax: Double = 180.0,
    /** Map of error type to error message for tabs with validation errors */
    val tabErrors: Map<ProfileErrorType, String> = emptyMap()
)

@HiltViewModel
@Stable
class ProfileEditorViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val localProfileManager: LocalProfileManager,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val insulin: Insulin,
    private val hardLimits: HardLimits,
    val dateUtil: DateUtil,
    private val protectionCheck: ProtectionCheck
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState>
        field = MutableStateFlow(ProfileUiState())

    init {
        loadState()
        subscribeToEvents()
    }

    private fun subscribeToEvents() {
        rxBus.toFlow(EventLocalProfileChanged::class.java)
            .onEach {
                aapsLogger.debug(LTag.PROFILE, "EventLocalProfileChanged received")
                loadState()
            }.launchIn(viewModelScope)
    }

    fun loadState() {
        val pumpDescription = activePlugin.activePump.pumpDescription
        val aps = activePlugin.activeAPS
        val profiles = localProfileManager.profile?.getProfileList()?.map { it.toString() } ?: emptyList()
        val currentProfile = localProfileManager.currentProfile()
        val isLocked = protectionCheck.isLocked(ProtectionCheck.Protection.PREFERENCES)

        val currentUnits = currentProfile?.mgdl?.let { if (it) GlucoseUnit.MGDL else GlucoseUnit.MMOL } ?: profileFunction.getUnits()
        val isMgdl = currentUnits == GlucoseUnit.MGDL

        // Get structured validation errors and build tab error map
        val validationErrors = localProfileManager.validateProfileStructured()
        val tabErrors = validationErrors
            .filter { it.type != ProfileErrorType.NAME || it.message != rh.gs(app.aaps.core.ui.R.string.profile_name_contains_dot) }
            .associateBy({ it.type }, { it.message })

        uiState.update { state ->
            state.copy(
                profiles = profiles,
                currentProfileIndex = localProfileManager.currentProfileIndex,
                currentProfile = currentProfile?.toState(),
                isEdited = localProfileManager.isEdited,
                isValid = localProfileManager.numOfProfiles > 0 && tabErrors.isEmpty(),
                isLocked = isLocked,
                units = currentUnits.asText,
                supportsDynamicIsf = aps.supportsDynamicIsf(),
                supportsDynamicIc = aps.supportsDynamicIc(),
                basalMin = pumpDescription.basalMinimumRate,
                basalMax = pumpDescription.basalMaximumRate.coerceAtMost(10.0),
                icMin = hardLimits.minIC(),
                icMax = hardLimits.maxIC(),
                isfMin = if (isMgdl) HardLimits.MIN_ISF else HardLimits.MIN_ISF / 18.0,
                isfMax = if (isMgdl) HardLimits.MAX_ISF else HardLimits.MAX_ISF / 18.0,
                targetMin = if (isMgdl) HardLimits.LIMIT_MIN_BG[0] else HardLimits.LIMIT_MIN_BG[0] / 18.0,
                targetMax = if (isMgdl) HardLimits.LIMIT_MAX_BG[1] else HardLimits.LIMIT_MAX_BG[1] / 18.0,
                tabErrors = tabErrors
            )
        }
    }

    fun selectTab(index: Int) {
        uiState.update { it.copy(selectedTab = index) }
    }

    /**
     * Select a profile by index for editing.
     * Called when navigating to the editor from ProfileManagementScreen.
     */
    fun selectProfile(index: Int) {
        localProfileManager.currentProfileIndex = index
        localProfileManager.isEdited = false
        loadState()
    }

    fun updateProfileName(name: String) {
        localProfileManager.currentProfile()?.name = name
        markEdited()
    }

    fun updateIcEntry(index: Int, timeValue: TimeValue) {
        localProfileManager.currentProfile()?.let { profile ->
            updateJsonArrayEntry(profile.ic, index, timeValue)
            markEdited()
        }
    }

    fun updateIsfEntry(index: Int, timeValue: TimeValue) {
        localProfileManager.currentProfile()?.let { profile ->
            updateJsonArrayEntry(profile.isf, index, timeValue)
            markEdited()
        }
    }

    fun updateBasalEntry(index: Int, timeValue: TimeValue) {
        localProfileManager.currentProfile()?.let { profile ->
            updateJsonArrayEntry(profile.basal, index, timeValue)
            markEdited()
        }
    }

    fun updateTargetEntry(index: Int, low: TimeValue, high: TimeValue) {
        localProfileManager.currentProfile()?.let { profile ->
            updateJsonArrayEntry(profile.targetLow, index, low)
            updateJsonArrayEntry(profile.targetHigh, index, high)
            markEdited()
        }
    }

    fun addIcEntry(afterIndex: Int) {
        localProfileManager.currentProfile()?.let { profile ->
            addJsonArrayEntry(profile.ic, afterIndex)
            markEdited()
        }
    }

    fun addIsfEntry(afterIndex: Int) {
        localProfileManager.currentProfile()?.let { profile ->
            addJsonArrayEntry(profile.isf, afterIndex)
            markEdited()
        }
    }

    fun addBasalEntry(afterIndex: Int) {
        localProfileManager.currentProfile()?.let { profile ->
            addJsonArrayEntry(profile.basal, afterIndex)
            markEdited()
        }
    }

    fun addTargetEntry(afterIndex: Int) {
        localProfileManager.currentProfile()?.let { profile ->
            addJsonArrayEntry(profile.targetLow, afterIndex)
            addJsonArrayEntry(profile.targetHigh, afterIndex)
            markEdited()
        }
    }

    fun removeIcEntry(index: Int) {
        localProfileManager.currentProfile()?.let { profile ->
            if (profile.ic.length() > 1 && index > 0) {
                profile.ic.remove(index)
                markEdited()
            }
        }
    }

    fun removeIsfEntry(index: Int) {
        localProfileManager.currentProfile()?.let { profile ->
            if (profile.isf.length() > 1 && index > 0) {
                profile.isf.remove(index)
                markEdited()
            }
        }
    }

    fun removeBasalEntry(index: Int) {
        localProfileManager.currentProfile()?.let { profile ->
            if (profile.basal.length() > 1 && index > 0) {
                profile.basal.remove(index)
                markEdited()
            }
        }
    }

    fun removeTargetEntry(index: Int) {
        localProfileManager.currentProfile()?.let { profile ->
            if (profile.targetLow.length() > 1 && index > 0) {
                profile.targetLow.remove(index)
                profile.targetHigh.remove(index)
                markEdited()
            }
        }
    }

    private fun updateJsonArrayEntry(array: JSONArray, index: Int, timeValue: TimeValue) {
        if (index < array.length()) {
            val obj = array.getJSONObject(index)
            val hour = timeValue.timeSeconds / 3600
            obj.put("time", String.format("%02d:00", hour))
            obj.put("timeAsSeconds", timeValue.timeSeconds)
            obj.put("value", timeValue.value)
        }
    }

    private fun addJsonArrayEntry(array: JSONArray, afterIndex: Int) {
        if (array.length() >= 24) return

        val prevObj = if (afterIndex >= 0 && afterIndex < array.length()) {
            array.getJSONObject(afterIndex)
        } else {
            null
        }

        val newTime = if (prevObj != null) {
            prevObj.getInt("timeAsSeconds") + 3600 // Add 1 hour after current entry
        } else {
            0
        }

        if (newTime >= 24 * 3600) return

        // Copy value from previous entry (the one before it)
        val inheritedValue = prevObj?.optDouble("value", 0.0) ?: 0.0

        val newObj = JSONObject().apply {
            val hour = newTime / 3600
            put("time", String.format("%02d:00", hour))
            put("timeAsSeconds", newTime)
            put("value", inheritedValue)
        }

        // Insert at position afterIndex + 1
        val insertPos = afterIndex + 1
        val tempList = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            tempList.add(array.getJSONObject(i))
        }
        tempList.add(insertPos.coerceIn(0, tempList.size), newObj)

        // Clear and rebuild array
        while (array.length() > 0) array.remove(0)
        tempList.forEach { array.put(it) }
    }

    private fun markEdited() {
        localProfileManager.isEdited = true
        loadState()
    }

    fun saveProfile() {
        viewModelScope.launch {
            localProfileManager.storeSettings(dateUtil.now())
            loadState()
        }
    }

    fun resetProfile() {
        localProfileManager.loadSettings()
        loadState()
    }

    fun getEditedProfile() = localProfileManager.getEditedProfile()

    fun getActiveInsulin() = insulin

    private fun LocalProfileManager.SingleProfile.toState(): SingleProfileState {
        return SingleProfileState(
            name = name,
            mgdl = mgdl,
            ic = ic.toTimeValueList(),
            isf = isf.toTimeValueList(),
            basal = basal.toTimeValueList(),
            targetLow = targetLow.toTimeValueList(),
            targetHigh = targetHigh.toTimeValueList()
        )
    }

    private fun JSONArray.toTimeValueList(): List<TimeValue> {
        val list = mutableListOf<TimeValue>()
        for (i in 0 until length()) {
            val obj = getJSONObject(i)
            list.add(
                TimeValue(
                    timeSeconds = obj.optInt("timeAsSeconds", 0),
                    value = obj.optDouble("value", 0.0)
                )
            )
        }
        return list
    }

    fun formatTime(seconds: Int): String {
        val hour = seconds / 3600
        return String.format("%02d:00", hour)
    }

    fun getBasalSum(): Double {
        return localProfileManager.getEditedProfile()?.let {
            ProfileSealed.Pure(it, null).baseBasalSum()
        } ?: 0.0
    }
}
