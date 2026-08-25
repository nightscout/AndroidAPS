package app.aaps.core.interfaces.pump

import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.resources.ResourceHelper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class BolusProgressDataTest {

    private val ch: ConcentrationHelper = mock()
    private val rh: ResourceHelper = mock()
    private lateinit var sut: BolusProgressData

    @BeforeEach
    fun setup() {
        // updateProgress(delivered) computes percent from fromPump() and builds status text via
        // bolusProgressString(); echo the delivered amount so percent reaches 100% and return non-null text.
        whenever(ch.fromPump(any<PumpInsulin>(), any<Boolean>())).thenAnswer { (it.arguments[0] as PumpInsulin).cU }
        whenever(ch.bolusProgressString(any<PumpInsulin>(), any<Boolean>())).thenReturn("")
        whenever(ch.bolusProgressString(any<PumpInsulin>(), any<Double>(), any<Boolean>())).thenReturn("")
        sut = BolusProgressData(ch, rh, TestScope())
    }

    @Test
    fun `start returns an increasing generation token`() {
        val first = sut.start(insulin = 0.25, isSMB = true)
        val second = sut.start(insulin = 1.1, isSMB = false)
        assertThat(second).isGreaterThan(first)
    }

    @Test
    fun `matching-generation clear wipes the current bolus state`() {
        val generation = sut.start(insulin = 1.1, isSMB = false)
        sut.clear(generation)
        assertThat(sut.state.value).isNull()
    }

    // Regression: an SMB (gen N) and a manual bolus (gen N+1) are both enqueued before either executes
    // (pump reconnecting). When the SMB finishes and clears with ITS generation, it must NOT wipe the
    // newer manual bolus's progress state — otherwise progress frames become no-ops and the driver reads
    // delivered = 0, raising a false BOLUS_DELIVERY_FAILED for a bolus that was fully delivered.
    @Test
    fun `stale-generation clear from an earlier SMB does not wipe a newer manual bolus`() {
        val smbGeneration = sut.start(insulin = 0.25, isSMB = true)
        val manualGeneration = sut.start(insulin = 1.1, isSMB = false)

        sut.clear(smbGeneration) // SMB finished — no-op because the manual bolus already started

        val state = sut.state.value
        assertThat(state).isNotNull()
        assertThat(state!!.insulin).isEqualTo(1.1) // still the manual bolus, not cleared
    }

    @Test
    fun `manual bolus progress still records after an earlier SMB clear`() {
        val smbGeneration = sut.start(insulin = 0.25, isSMB = true)
        sut.start(insulin = 1.1, isSMB = false)

        sut.clear(smbGeneration)               // earlier SMB clears — must be a no-op
        sut.updateProgress(PumpInsulin(1.1))   // pump reports full delivery of the manual bolus

        assertThat(sut.state.value?.delivered?.cU).isEqualTo(1.1)
        assertThat(sut.state.value?.percent).isEqualTo(100)
    }

    // Abort paths (connection timeout, cancelAllBoluses, remote Cleared) keep the unconditional clear():
    // it must still wipe the state even when a newer bolus has bumped the generation.
    @Test
    fun `unconditional clear wipes state even after a newer bolus started`() {
        val smbGeneration = sut.start(insulin = 0.25, isSMB = true)
        sut.start(insulin = 1.1, isSMB = false)

        sut.clear(smbGeneration) // stale generation — no-op
        sut.clear()              // abort-everything — must wipe regardless of generation

        assertThat(sut.state.value).isNull()
    }

    @Test
    fun `updateProgress is a no-op when no bolus is in progress`() {
        sut.clear() // state == null

        sut.updateProgress(PumpInsulin(0.5))

        assertThat(sut.state.value).isNull()
    }
}
