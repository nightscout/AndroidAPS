package app.aaps.implementation.profile

import app.aaps.core.data.model.EPS
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
        whenever(persistenceLayer.observeChanges(EPS::class.java)).thenReturn(upstream)

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
        whenever(persistenceLayer.observeChanges(EPS::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch)

        // The running EPS is authoritative — a pending switch must not override what the pump is actually using.
        assertThat(createSut().getRunningOrRequestedICfg()).isEqualTo(effectiveProfileSwitch.iCfg)
    }

    @Test
    fun withNothingRunningFallsBackToARequestedSwitch() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(profileSwitch)

        // No EPS yet (the pump hasn't confirmed), but the user already chose — still a real choice, not a guess.
        assertThat(createSut().getRunningOrRequestedICfg()).isEqualTo(profileSwitch.iCfg)
    }

    @Test
    fun withNothingRunningOrRequestedItIsNull() = runTest {
        whenever(persistenceLayer.observeChanges(EPS::class.java)).thenReturn(emptyFlow())
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(null)
        whenever(persistenceLayer.getProfileSwitchActiveAt(anyLong())).thenReturn(null)

        // Null means "ask the user or refuse" — never substitute a catalogue entry.
        assertThat(createSut().getRunningOrRequestedICfg()).isNull()
    }
}
