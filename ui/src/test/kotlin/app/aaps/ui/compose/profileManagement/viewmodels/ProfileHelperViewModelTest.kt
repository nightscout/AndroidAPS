package app.aaps.ui.compose.profileManagement.viewmodels

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.ui.compose.profileHelper.ProfileType
import app.aaps.ui.compose.profileHelper.defaultProfile.DefaultProfile
import app.aaps.ui.compose.profileHelper.defaultProfile.DefaultProfileDPV
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileHelperViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var tddCalculator: TddCalculator
    @Mock private lateinit var defaultProfile: DefaultProfile
    @Mock private lateinit var defaultProfileDPV: DefaultProfileDPV
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var fabricPrivacy: FabricPrivacy

    private lateinit var sut: ProfileHelperViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher does NOT run the init{}-launched loadInitialData()/refreshCurrentProfile()
        // coroutines (no advanceUntilIdle), so construction stays clean and we test the synchronous getters
        // against the default uiState. Only the EPS observeChanges cold flow chain is built synchronously.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(persistenceLayer.observeChanges(EPS::class.java)).thenReturn(emptyFlow())
        sut = ProfileHelperViewModel(
            persistenceLayer, profileRepository, profileFunction, profileUtil, rh, dateUtil,
            tddCalculator, defaultProfile, defaultProfileDPV, rxBus, fabricPrivacy
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState has empty selections and is loading stats`() {
        val state = sut.uiState.value
        assertThat(state.currentProfileName).isEqualTo("")
        assertThat(state.currentProfile).isNull()
        assertThat(state.availableProfiles).isEmpty()
        assertThat(state.profileSwitches).isEmpty()
        assertThat(state.tddStatsData).isNull()
        assertThat(state.isLoadingStats).isTrue()
    }

    @Test
    fun `getUnits delegates to profileFunction`() {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)

        assertThat(sut.getUnits()).isEqualTo(GlucoseUnit.MGDL)
    }

    @Test
    fun `getProfileName for CURRENT returns the default current profile name`() {
        val name = sut.getProfileName(
            age = 10, tdd = 0.0, weight = 30.0, basalPct = 0.5,
            profileType = ProfileType.CURRENT, profileIndex = 0, profileSwitchIndex = 0
        )
        assertThat(name).isEqualTo("")
    }

    @Test
    fun `getProfileName for PROFILE_SWITCH out of range returns empty string`() {
        val name = sut.getProfileName(
            age = 10, tdd = 0.0, weight = 30.0, basalPct = 0.5,
            profileType = ProfileType.PROFILE_SWITCH, profileIndex = 0, profileSwitchIndex = 0
        )
        assertThat(name).isEqualTo("")
    }

    @Test
    fun `getProfile for CURRENT returns the default null current profile`() {
        val profile = sut.getProfile(
            age = 10, tdd = 0.0, weight = 30.0, basalPct = 0.5,
            profileType = ProfileType.CURRENT, profileIndex = 0, profileSwitchIndex = 0
        )
        assertThat(profile).isNull()
    }

    @Test
    fun `getProfile for PROFILE_SWITCH out of range returns null`() {
        val profile = sut.getProfile(
            age = 10, tdd = 0.0, weight = 30.0, basalPct = 0.5,
            profileType = ProfileType.PROFILE_SWITCH, profileIndex = 0, profileSwitchIndex = 0
        )
        assertThat(profile).isNull()
    }
}
