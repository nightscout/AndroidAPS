package app.aaps.implementation.profile

import app.aaps.core.data.model.EPS
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
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
}
