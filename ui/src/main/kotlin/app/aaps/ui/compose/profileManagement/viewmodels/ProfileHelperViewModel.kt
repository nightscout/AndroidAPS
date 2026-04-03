package app.aaps.ui.compose.profileManagement.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.ui.R
import app.aaps.ui.compose.profileHelper.ProfileType
import app.aaps.ui.compose.profileHelper.defaultProfile.DefaultProfile
import app.aaps.ui.compose.profileHelper.defaultProfile.DefaultProfileDPV
import app.aaps.ui.compose.stats.TddStatsData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel for ProfileHelperScreen managing profile comparison state and business logic.
 */
@HiltViewModel
@Stable
class ProfileHelperViewModel @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val localProfileManager: LocalProfileManager,
    private val profileFunction: ProfileFunction,
    val profileUtil: ProfileUtil,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val tddCalculator: TddCalculator,
    private val defaultProfile: DefaultProfile,
    private val defaultProfileDPV: DefaultProfileDPV,
    private val uiInteraction: UiInteraction,
    private val fabricPrivacy: FabricPrivacy
) : ViewModel() {

    val uiState: StateFlow<ProfileHelperUiState>
        field = MutableStateFlow(ProfileHelperUiState())

    private var cachedCurrentProfile: PureProfile? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val currentProfileName = profileFunction.getProfileName()
            val currentProfile = profileFunction.getProfile()?.convertToNonCustomizedProfile(dateUtil)
            cachedCurrentProfile = currentProfile
            val availableProfiles = localProfileManager.profile?.getProfileList() ?: ArrayList()
            val profileSwitches = withContext(Dispatchers.IO) {
                persistenceLayer.getEffectiveProfileSwitchesFromTime(
                    dateUtil.now() - T.months(2).msecs(),
                    true
                )
            }

            uiState.update {
                it.copy(
                    currentProfileName = currentProfileName,
                    availableProfiles = availableProfiles,
                    profileSwitches = profileSwitches
                )
            }

            loadTddStats()
        }
    }

    private fun loadTddStats() {
        viewModelScope.launch {
            uiState.update { it.copy(isLoadingStats = true) }
            try {
                val data = withContext(Dispatchers.IO) {
                    val tdds = tddCalculator.calculate(7, allowMissingDays = true)
                    val averageTdd = tddCalculator.averageTDD(tdds)
                    val todayTdd = tddCalculator.calculateToday()
                    TddStatsData(tdds = tdds, averageTdd = averageTdd, todayTdd = todayTdd)
                }
                uiState.update { it.copy(tddStatsData = data, isLoadingStats = false) }
            } catch (e: Exception) {
                fabricPrivacy.logException(e)
                uiState.update { it.copy(isLoadingStats = false) }
            }
        }
    }

    fun getUnits(): GlucoseUnit = profileFunction.getUnits()

    /**
     * Get profile based on type and parameters
     */
    fun getProfile(
        age: Int,
        tdd: Double,
        weight: Double,
        basalPct: Double,
        profileType: ProfileType,
        profileIndex: Int,
        profileSwitchIndex: Int
    ): PureProfile? {
        return try {
            when (profileType) {
                ProfileType.MOTOL_DEFAULT     -> defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())
                ProfileType.DPV_DEFAULT       -> defaultProfileDPV.profile(age, tdd, basalPct, profileFunction.getUnits())
                ProfileType.CURRENT           -> cachedCurrentProfile

                ProfileType.AVAILABLE_PROFILE -> {
                    val list = localProfileManager.profile?.getProfileList()
                    if (list != null && profileIndex < list.size)
                        localProfileManager.profile?.getSpecificProfile(list[profileIndex].toString())
                    else null
                }

                ProfileType.PROFILE_SWITCH    -> runBlocking {
                    val switches = persistenceLayer.getEffectiveProfileSwitchesFromTime(
                        dateUtil.now() - T.months(2).msecs(),
                        true
                    )
                    if (profileSwitchIndex < switches.size)
                        ProfileSealed.EPS(value = switches[profileSwitchIndex], activePlugin = null)
                            .convertToNonCustomizedProfile(dateUtil)
                    else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get profile name based on type and parameters
     */
    fun getProfileName(
        age: Int,
        tdd: Double,
        weight: Double,
        basalPct: Double,
        profileType: ProfileType,
        profileIndex: Int,
        profileSwitchIndex: Int
    ): String {
        return when (profileType) {
            ProfileType.MOTOL_DEFAULT     -> if (tdd > 0) rh.gs(R.string.format_with_tdd, age, tdd)
            else rh.gs(R.string.format_with_weight, age, weight)

            ProfileType.DPV_DEFAULT       -> rh.gs(R.string.format_with_tdd_and_pct, age, tdd, (basalPct * 100).toInt())
            ProfileType.CURRENT           -> uiState.value.currentProfileName

            ProfileType.AVAILABLE_PROFILE -> {
                val list = localProfileManager.profile?.getProfileList()
                if (list != null && profileIndex < list.size) list[profileIndex].toString() else ""
            }

            ProfileType.PROFILE_SWITCH    -> runBlocking {
                val switches = persistenceLayer.getEffectiveProfileSwitchesFromTime(
                    dateUtil.now() - T.months(2).msecs(),
                    true
                )
                if (profileSwitchIndex < switches.size) switches[profileSwitchIndex].originalCustomizedName else ""
            }
        }
    }

    /**
     * Copy generated profile to local profiles
     */
    fun copyToLocal(
        context: Context,
        age: Int,
        tdd: Double,
        weight: Double,
        pct: Double,
        profileType: ProfileType
    ) {
        val profile = if (profileType == ProfileType.MOTOL_DEFAULT)
            defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())
        else
            defaultProfileDPV.profile(age, tdd, pct / 100.0, profileFunction.getUnits())

        profile?.let {
            uiInteraction.showOkCancelDialog(
                context = context,
                title = rh.gs(app.aaps.core.ui.R.string.careportal_profileswitch),
                message = rh.gs(app.aaps.core.ui.R.string.copytolocalprofile),
                ok = {
                    localProfileManager.addProfile(
                        localProfileManager.copyFrom(
                            it,
                            "DefaultProfile " + dateUtil.dateAndTimeAndSecondsString(dateUtil.now()).replace(".", "/")
                        )
                    )
                }
            )
        }
    }
}

/**
 * UI state for ProfileHelperScreen
 */
@Immutable
data class ProfileHelperUiState(
    val currentProfileName: String = "",
    val availableProfiles: List<CharSequence> = emptyList(),
    val profileSwitches: List<EPS> = emptyList(),
    val tddStatsData: TddStatsData? = null,
    val isLoadingStats: Boolean = true
)
