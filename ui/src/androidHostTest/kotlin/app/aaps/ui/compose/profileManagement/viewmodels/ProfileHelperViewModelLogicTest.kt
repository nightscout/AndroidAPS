package app.aaps.ui.compose.profileManagement.viewmodels

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.ui.compose.profileHelper.ProfileType
import app.aaps.ui.compose.profileHelper.defaultProfile.DefaultProfile
import app.aaps.ui.compose.profileHelper.defaultProfile.DefaultProfileDPV
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the parts of [ProfileHelperViewModel] a user can see: which label template each profile
 * type gets and with which arguments, which generator a profile type is built from and with which
 * scaling, and what the "copy to local profile" action asks before it writes.
 *
 * The resolver here is a hand written fake rather than a mock, so it answers the [TextRef] form the
 * shared code really calls and the assertions pin the produced text, not the overload.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileHelperViewModelLogicTest {

    /** Renders every request as `name|arg,arg` so a test can assert both template and arguments. */
    private class FakeTextResources : TextResolver {

        private fun nameOf(ref: TextRef): String = if (ref is TextRef.Named) ref.name else ref.toString()
        override fun gs(ref: TextRef): String = nameOf(ref)
        override fun gs(ref: TextRef, vararg args: Any?): String =
            nameOf(ref) + "|" + args.joinToString(",")

        override fun gsNotLocalised(ref: TextRef): String = nameOf(ref)
        override fun shortTextMode(): Boolean = false
    }

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var tddCalculator: TddCalculator
    @Mock private lateinit var defaultProfile: DefaultProfile
    @Mock private lateinit var defaultProfileDPV: DefaultProfileDPV
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var fabricPrivacy: FabricPrivacy

    private val rh = FakeTextResources()
    private lateinit var sut: ProfileHelperViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher leaves the init{} coroutines unrun, so construction stays clean and
        // the synchronous getters below are read against the default state.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(null))
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        sut = ProfileHelperViewModel(
            persistenceLayer, profileRepository, profileFunction, profileUtil, rh, dateUtil,
            tddCalculator, defaultProfile, defaultProfileDPV, rxBus, fabricPrivacy
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    // ---- label templates -------------------------------------------------------------------

    @Test
    fun `MOTOL label uses the TDD template when a TDD is entered`() {
        val name = sut.getProfileName(
            age = 12, tdd = 30.0, weight = 40.0, basalPct = 0.32,
            profileType = ProfileType.MOTOL_DEFAULT, profileIndex = 0, profileSwitchIndex = 0
        )
        assertThat(name).isEqualTo("format_with_tdd|12,30.0")
    }

    @Test
    fun `MOTOL label uses the weight template when there is no TDD`() {
        val name = sut.getProfileName(
            age = 12, tdd = 0.0, weight = 40.0, basalPct = 0.32,
            profileType = ProfileType.MOTOL_DEFAULT, profileIndex = 0, profileSwitchIndex = 0
        )
        assertThat(name).isEqualTo("format_with_weight|12,40.0")
    }

    @Test
    fun `DPV label carries the basal percentage as a whole number`() {
        val name = sut.getProfileName(
            age = 12, tdd = 30.0, weight = 40.0, basalPct = 0.35,
            profileType = ProfileType.DPV_DEFAULT, profileIndex = 0, profileSwitchIndex = 0
        )
        assertThat(name).isEqualTo("format_with_tdd_and_pct|12,30.0,35")
    }

    @Test
    fun `AVAILABLE_PROFILE label is empty when the index is out of range`() {
        val name = sut.getProfileName(
            age = 12, tdd = 30.0, weight = 40.0, basalPct = 0.32,
            profileType = ProfileType.AVAILABLE_PROFILE, profileIndex = 3, profileSwitchIndex = 0
        )
        assertThat(name).isEqualTo("")
    }

    // ---- which generator builds which profile ----------------------------------------------

    @Test
    fun `MOTOL profile is built by DefaultProfile from age tdd and weight`() {
        val expected: PureProfile = mock()
        whenever(defaultProfile.profile(12, 30.0, 40.0, GlucoseUnit.MGDL)).thenReturn(expected)

        val profile = sut.getProfile(
            age = 12, tdd = 30.0, weight = 40.0, basalPct = 0.32,
            profileType = ProfileType.MOTOL_DEFAULT, profileIndex = 0, profileSwitchIndex = 0
        )

        assertThat(profile).isSameInstanceAs(expected)
        verify(defaultProfileDPV, never()).profile(any(), any(), any(), any())
    }

    @Test
    fun `DPV profile is built by DefaultProfileDPV and the fraction is passed through unscaled`() {
        val expected: PureProfile = mock()
        whenever(defaultProfileDPV.profile(12, 30.0, 0.32, GlucoseUnit.MGDL)).thenReturn(expected)

        val profile = sut.getProfile(
            age = 12, tdd = 30.0, weight = 40.0, basalPct = 0.32,
            profileType = ProfileType.DPV_DEFAULT, profileIndex = 0, profileSwitchIndex = 0
        )

        assertThat(profile).isSameInstanceAs(expected)
    }

    @Test
    fun `a generator failure shows no profile instead of crashing the screen`() {
        whenever(defaultProfile.profile(12, 30.0, 40.0, GlucoseUnit.MGDL))
            .thenThrow(IllegalStateException("boom"))

        val profile = sut.getProfile(
            age = 12, tdd = 30.0, weight = 40.0, basalPct = 0.32,
            profileType = ProfileType.MOTOL_DEFAULT, profileIndex = 0, profileSwitchIndex = 0
        )

        assertThat(profile).isNull()
    }

    // ---- copy to local ---------------------------------------------------------------------

    @Test
    fun `copy to local asks for confirmation with the profile switch title`() {
        whenever(defaultProfile.profile(12, 30.0, 40.0, GlucoseUnit.MGDL)).thenReturn(mock())

        sut.copyToLocal(age = 12, tdd = 30.0, weight = 40.0, pct = 32.0, profileType = ProfileType.MOTOL_DEFAULT)

        val captor = argumentCaptor<EventShowDialog.OkCancel>()
        verify(rxBus).send(captor.capture())
        assertThat(captor.firstValue.title).isEqualTo("careportal_profileswitch")
        assertThat(captor.firstValue.message.toString()).isEqualTo("copytolocalprofile")
    }

    @Test
    fun `copy to local of a DPV profile turns the percent into a fraction`() {
        whenever(defaultProfileDPV.profile(12, 30.0, 0.32, GlucoseUnit.MGDL)).thenReturn(mock())

        sut.copyToLocal(age = 12, tdd = 30.0, weight = 40.0, pct = 32.0, profileType = ProfileType.DPV_DEFAULT)

        verify(defaultProfileDPV).profile(12, 30.0, 0.32, GlucoseUnit.MGDL)
        verify(rxBus).send(any<EventShowDialog.OkCancel>())
    }

    @Test
    fun `copy to local asks nothing when no profile could be generated`() {
        whenever(defaultProfile.profile(eq(12), eq(30.0), eq(40.0), eq(GlucoseUnit.MGDL))).thenReturn(null)

        sut.copyToLocal(age = 12, tdd = 30.0, weight = 40.0, pct = 32.0, profileType = ProfileType.MOTOL_DEFAULT)

        verify(rxBus, never()).send(any())
    }
}
