package app.aaps.implementation.profile

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
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
}
