package app.aaps.implementation.profile

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.CoreUiStrings
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileFunctionImplTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer

    @Test
    fun cacheCollectorSurvivesUpstreamFailure() = runTest {
        // Regression: a failure in the EPS-change source must not permanently kill the cache-invalidation
        // collector. An unguarded collector would be cancelled on the first upstream error and never
        // invalidate the profile cache again (stale getProfile() until restart). collectResilient restarts it.
        var attempts = 0
        val upstream: Flow<List<EPS>> = flow {
            attempts++
            emit(listOf(effectiveProfileSwitch))
            if (attempts == 1) throw RuntimeException("induced upstream failure")
            // 2nd collection completes normally -> no further retry
        }
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(upstream)

        ProfileFunctionImpl(
            aapsLogger, preferences, rh, activePlugin, profileRepository,
            persistenceLayer, dateUtil, config, hardLimits, notificationManager,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )
        advanceUntilIdle()

        // Unguarded: upstream throws -> flow cancelled -> collected once (attempts == 1).
        // collectResilient: restarts after the failure -> upstream re-collected (attempts == 2).
        assertThat(attempts).isEqualTo(2)
    }

    // The synchronous mirror read by non-suspend callers (concentration conversions, Compose). It is
    // refreshed inside the same subscription that invalidates the cache, deliberately: a second, independent
    // collector on this flow has no ordering guarantee against the invalidation and could re-read the stale
    // cache, pinning the mirror to the previous insulin until the next profile change.
    @Test
    fun runningICfgMirrorFollowsEffectiveProfileChanges() = runTest {
        val changes = MutableSharedFlow<List<EPS>>(extraBufferCapacity = 8)
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(changes)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(null)

        val sut = ProfileFunctionImpl(
            aapsLogger, preferences, rh, activePlugin, profileRepository,
            persistenceLayer, dateUtil, config, hardLimits, notificationManager,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        )
        advanceUntilIdle()
        assertThat(sut.runningICfg.value).isNull() // nothing running yet

        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch)
        changes.tryEmit(listOf(effectiveProfileSwitch))
        advanceUntilIdle()

        assertThat(sut.runningICfg.value).isEqualTo(effectiveProfileSwitch.iCfg)
    }

    // ---------------------------------------------------------------------------------------------
    // getRunningOrRequestedICfg precedence
    //
    // Every caller that records an insulin resolves it through here, and callers mock ProfileFunction —
    // so this is the one place the real rule is exercised. There is deliberately no third tier: the
    // insulin catalogue is a list to choose from, never a source of "the current one".
    // ---------------------------------------------------------------------------------------------

    private fun TestScope.createSut() = ProfileFunctionImpl(
        aapsLogger, preferences, rh, activePlugin, profileRepository,
        persistenceLayer, dateUtil, config, hardLimits, notificationManager,
        CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    )

    @Test
    fun runningProfileWins() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch)

        // The running EPS is authoritative — a pending switch must not override what the pump is actually using.
        assertThat(createSut().getRunningOrRequestedICfg()).isEqualTo(effectiveProfileSwitch.iCfg)
    }

    @Test
    fun withNothingRunningFallsBackToARequestedSwitch() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(null)
        // The shared fixture's iCfg is ICfg("", 0, 0) — degenerate, and now correctly rejected as unusable —
        // so give this one a real insulin, which is what the case being tested actually describes.
        val requested = profileSwitch.copy(iCfg = someICfg)
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(requested)

        // No EPS yet (the pump hasn't confirmed), but the user already chose — still a real choice, not a guess.
        assertThat(createSut().getRunningOrRequestedICfg()).isEqualTo(someICfg)
    }

    // Regression, issue #5020: the DB v33 SQL step stamps `insulinEndTime = -1` onto every pre-ICfg row —
    // the *running* profile's included. Returning that as a value made the migration write the sentinel back
    // over itself, leaving a DIA of 0.0 that fails the hard-limit check and aborts every loop cycle. Reporting
    // "nothing in force" instead sends MainApp down its substitute path, which repairs the rows; since the
    // repair re-runs every start and still matches the sentinel, an already-broken install heals on relaunch.
    @Test
    fun aRunningProfileCarryingTheMigrationSentinelCountsAsNothingInForce() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        val sentinelEps = effectiveProfileSwitch.copy(iCfg = ICfg(insulinLabel = "", insulinEndTime = -1, insulinPeakTime = -1, concentration = 1.0))
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(sentinelEps)
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(null)

        assertThat(createSut().getRunningOrRequestedICfg()).isNull()
    }

    // …and the same for a requested switch that was stamped with the sentinel: still not a value.
    @Test
    fun aRequestedSwitchCarryingTheSentinelCountsAsNothingInForce() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong()))
            .thenReturn(profileSwitch.copy(iCfg = ICfg(insulinLabel = "", insulinEndTime = -1, insulinPeakTime = -1, concentration = 1.0)))

        assertThat(createSut().getRunningOrRequestedICfg()).isNull()
    }

    // The synchronous mirror must agree — non-suspend readers would otherwise still see the sentinel.
    @Test
    fun theMirrorAlsoRejectsTheSentinel() = runTest {
        val changes = MutableSharedFlow<List<EPS>>(extraBufferCapacity = 8)
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(changes)
        val sentinelEps = effectiveProfileSwitch.copy(iCfg = ICfg(insulinLabel = "", insulinEndTime = -1, insulinPeakTime = -1, concentration = 1.0))
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(sentinelEps)

        val sut = createSut()
        advanceUntilIdle()
        changes.tryEmit(listOf(sentinelEps))
        advanceUntilIdle()

        assertThat(sut.runningICfg.value).isNull()
    }

    @Test
    fun withNothingRunningOrRequestedItIsNull() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(null)

        // Null means "ask the user or refuse" — never substitute a catalogue entry.
        assertThat(createSut().getRunningOrRequestedICfg()).isNull()
    }

    // Dedup: getProfile() caches per second, but every distinct second within one profile-switch window
    // must reuse a SINGLE deep copy of the effective profile rather than retaining a duplicate per second.
    // getEffectiveProfileSwitchActiveAt() deep-copies a fresh EPS on every real DB read, so model that with
    // thenAnswer { copy() } (distinct instances, same id) — the assertion then proves those later copies are
    // dropped and the whole window shares the first one.
    @Test
    fun secondsInOneWindowShareASingleCanonicalEps() = runTest {
        whenever(config.appInitialized).thenReturn(true)
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        // Fresh instance per call (same id), mirroring fromDb()'s deep copy.
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenAnswer { effectiveProfileSwitch.copy() }

        val sut = createSut()
        advanceUntilIdle()
        // The init{} mirror read already populated the maps at "now"; reset so the query set below is exact.
        sut.cache.clear()
        sut.canonicalEps.clear()

        // Five distinct seconds within the same window (all still the same active EPS).
        val base = 1_000_000_000L
        val profiles = (0 until 5).map { sut.getProfile(base + it * 1000L) }

        // One cache entry per distinct second…
        assertThat(sut.cache).hasSize(5)
        // …but a single shared deep copy of the profile, not five.
        assertThat(sut.canonicalEps).hasSize(1)
        // …and every returned wrapper points at that one shared EPS instance (the throwaway copies were dropped).
        val shared = (profiles.first() as ProfileSealed.EPS).value
        assertThat(profiles.all { (it as ProfileSealed.EPS).value === shared }).isTrue()
    }

    // ---------------------------------------------------------------------------------------------
    // The percentage-only overload (wear / CPP / automation), which had no time parameter at all.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aBackDatedPercentageSwitchResolvesTheBaseProfileAtThatTime() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getPermanentProfileSwitchActiveAt(anyLong())).thenReturn(null)
        val at = dateUtil.now() - T.hours(3).msecs()

        // With text: a switch with no profile in force logs why.
        createSutWithText().createProfileSwitch(
            durationInMinutes = 30, percentage = 80, timeShiftInHours = 0,
            action = Action.PROFILE_SWITCH, source = Sources.Automation, listValues = emptyList(), timestamp = at
        )

        // The base the percentage applies to is the profile that really was running then, not the one now.
        verify(persistenceLayer).getPermanentProfileSwitchActiveAt(at)
    }

    @Test
    fun aPercentageSwitchWithoutATimeUsesNow() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getPermanentProfileSwitchActiveAt(anyLong())).thenReturn(null)

        val before = dateUtil.now()
        // With text: a switch with no profile in force logs why.
        createSutWithText().createProfileSwitch(
            durationInMinutes = 30, percentage = 80, timeShiftInHours = 0,
            action = Action.PROFILE_SWITCH, source = Sources.Automation, listValues = emptyList()
        )
        val after = dateUtil.now()

        val at = argumentCaptor<Long>()
        verify(persistenceLayer).getPermanentProfileSwitchActiveAt(at.capture())
        assertThat(at.firstValue).isAtLeast(before)
        assertThat(at.firstValue).isAtMost(after)
    }

    // ---------------------------------------------------------------------------------------------
    // Nothing before app init
    //
    // Until init completes activePlugin.activeAPS is null, and ProfileSealed takes the APS when it is
    // built, so a profile from that time keeps aps = null for good. It reached a wizard calculation after
    // init through the per-second cache and crashed on "APS not defined".
    // ---------------------------------------------------------------------------------------------

    @Test
    fun noProfileIsHandedOutOrCachedBeforeAppInit() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch)
        whenever(config.appInitialized).thenReturn(false)
        val sut = createSut()
        advanceUntilIdle()

        assertThat(sut.getProfile()).isNull()
        assertThat(sut.cache).isEmpty()

        whenever(config.appInitialized).thenReturn(true)
        assertThat(sut.getProfile()).isNotNull()
    }

    // The pump needs the insulin concentration during start up: PumpWithConcentrationImpl falls back to 1.0
    // without it. So the mirror must not wait for app init.
    @Test
    fun theInsulinMirrorIsFilledBeforeAppInit() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch.copy(iCfg = someICfg))
        whenever(config.appInitialized).thenReturn(false)

        val sut = createSut()
        advanceUntilIdle()

        assertThat(sut.runningICfg.value).isEqualTo(someICfg)
    }

    // The data migrations in MainApp run before app init. With no answer here they would stamp old records
    // with a substitute insulin instead of the running one.
    @Test
    fun theRunningInsulinIsKnownBeforeAppInit() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch.copy(iCfg = someICfg))
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(null)
        whenever(config.appInitialized).thenReturn(false)

        assertThat(createSut().getRunningOrRequestedICfg()).isEqualTo(someICfg)
    }

    // ---------------------------------------------------------------------------------------------
    // profileSwitchRefusal: the check createProfileSwitch runs, with the reason for the user
    // ---------------------------------------------------------------------------------------------

    /** With the real English text, so the reason asserted is the one the user reads. */
    private fun TestScope.createSutWithText() = ProfileFunctionImpl(
        aapsLogger, preferences, baseText, activePlugin, profileRepository,
        persistenceLayer, dateUtil, config, hardLimits, notificationManager,
        CoroutineScope(UnconfinedTestDispatcher(testScheduler))
    )

    @Test
    fun aSwitchThePumpAndTheHardLimitsAcceptIsNotRefused() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())

        assertThat(createSutWithText().profileSwitchRefusal(getValidProfileStore(), TESTPROFILENAME, 0, 100, 0, someICfg)).isNull()
    }

    /** The profile basal is 1 U/h: at 150 % it is 1.5 U/h, above a pump max basal of 1.2 U/h. */
    @Test
    fun aBasalAboveTheMaxBasalOfThePumpIsRefusedWithThePumpReason() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        testPumpPlugin.pumpDescription.basalMaximumRate = 1.2

        assertThat(createSutWithText().profileSwitchRefusal(getValidProfileStore(), TESTPROFILENAME, 0, 150, 0, someICfg))
            .isEqualTo("Basal rate is not compatible with the current pump and cannot be activated. Reduce the percentage or adjust the basal rate.")
    }

    @Test
    fun aProfileThatIsNotInTheStoreIsRefused() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())

        assertThat(createSutWithText().profileSwitchRefusal(getValidProfileStore(), "no such profile", 0, 100, 0, someICfg))
            .isEqualTo(baseText.gs(CoreUiStrings.notexists))
    }

    /** The profile basal is 1 U/h: at 30 % it is 0.3 U/h, below a pump min basal of 0.5 U/h. "Reduce" would be wrong. */
    @Test
    fun aBasalBelowTheMinBasalOfThePumpIsRefusedWithItsOwnReason() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        testPumpPlugin.pumpDescription.basalMinimumRate = 0.5

        assertThat(createSutWithText().profileSwitchRefusal(getValidProfileStore(), TESTPROFILENAME, 0, 30, 0, someICfg))
            .isEqualTo("Basal rate is below the minimum of the current pump and cannot be activated. Raise the percentage or adjust the basal rate.")
    }

    @Test
    fun aBasalNotOnFullHoursIsRefusedByAPumpThatTakesOnlyFullHours() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        testPumpPlugin.pumpDescription.is30minBasalRatesCapable = false

        assertThat(createSutWithText().profileSwitchRefusal(storeWithAHalfHourBasal(), TESTPROFILENAME, 0, 100, 0, someICfg))
            .isEqualTo(baseText.gs(InterfacesStrings.basalprofilenotaligned, baseText.gs(CoreUiStrings.careportal_profileswitch)))
    }

    /**
     * The valid test profile, but its basal changes at 0:30. The profile reader refuses such a schedule, so it
     * cannot come from a stored profile, and the store here is a mock.
     */
    private fun storeWithAHalfHourBasal(): ProfileStore {
        val pure = getValidProfileStore().getSpecificProfile(TESTPROFILENAME)!!
        pure.basalBlocks = listOf(Block(T.mins(30L).msecs(), 1.0), Block(T.mins(23L * 60 + 30).msecs(), 1.2))
        return mock { on { getSpecificProfile(TESTPROFILENAME) } doReturn pure }
    }

    // A change of the active profile: each reason why there is nothing to change has its own sentence.

    @Test
    fun changingTheActiveProfileWithNoProfileSwitchSaysThatNoProfileIsSet() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getPermanentProfileSwitchActiveAt(anyLong())).thenReturn(null)

        assertThat(createSutWithText().profileSwitchRefusal(60, 120, 0)).isEqualTo(baseText.gs(CoreUiStrings.no_profile_set))
    }

    @Test
    fun changingTheActiveProfileWithNoProfileStoreSaysThatNoProfileIsLoaded() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getPermanentProfileSwitchActiveAt(anyLong())).thenReturn(profileSwitch.copy(profileName = TESTPROFILENAME))
        whenever(profileRepository.profile).thenReturn(MutableStateFlow<ProfileStore?>(null))

        assertThat(createSutWithText().profileSwitchRefusal(60, 120, 0)).isEqualTo(baseText.gs(CoreUiStrings.noprofile))
    }

    @Test
    fun changingAnActiveProfileThatIsNotInTheStoreNamesIt() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getPermanentProfileSwitchActiveAt(anyLong())).thenReturn(profileSwitch.copy(profileName = "Gone"))

        assertThat(createSutWithText().profileSwitchRefusal(60, 120, 0)).isEqualTo(baseText.gs(CoreUiStrings.scene_profile_not_found, "Gone"))
    }

    // insulinSwitchRefusal: the same check, for the switch that changes only the insulin of the running profile

    @Test
    fun anInsulinSwitchWithNoRunningProfileSaysThatNoProfileIsSet() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(config.appInitialized).thenReturn(true)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(null)

        assertThat(createSutWithText().insulinSwitchRefusal(someICfg)).isEqualTo(baseText.gs(CoreUiStrings.no_profile_set))
    }

    /** The running profile is at 150 %: 1.5 U/h, above a pump max basal of 1.2 U/h. A new insulin does not change that. */
    @Test
    fun anInsulinSwitchThePumpCannotTakeSaysWhy() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(config.appInitialized).thenReturn(true)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong()))
            .thenReturn(effectiveProfileSwitch.copy(originalProfileName = TESTPROFILENAME, originalPercentage = 150))
        testPumpPlugin.pumpDescription.basalMaximumRate = 1.2

        assertThat(createSutWithText().insulinSwitchRefusal(someICfg))
            .isEqualTo("Basal rate is not compatible with the current pump and cannot be activated. Reduce the percentage or adjust the basal rate.")
    }

    @Test
    fun anInsulinSwitchThatFitsIsNotRefused() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        whenever(config.appInitialized).thenReturn(true)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong()))
            .thenReturn(effectiveProfileSwitch.copy(originalProfileName = TESTPROFILENAME, originalPercentage = 100))

        assertThat(createSutWithText().insulinSwitchRefusal(someICfg)).isNull()
    }

    @Test
    fun changingTheActiveProfileThatThePumpAcceptsIsNotRefused() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class)).thenReturn(emptyFlow())
        // The fixture's insulin has a DIA of 0, which the hard limits refuse. A real one here.
        whenever(persistenceLayer.getPermanentProfileSwitchActiveAt(anyLong())).thenReturn(profileSwitch.copy(profileName = TESTPROFILENAME, iCfg = someICfg))

        assertThat(createSutWithText().profileSwitchRefusal(60, 120, 0)).isNull()
    }
}
