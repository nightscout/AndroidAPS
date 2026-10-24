package app.aaps.pump.carelevo

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import java.util.Optional

class CarelevoPumpPluginTest : CarelevoPumpPluginTestBase() {

    @Test
    fun `manufacturer should return Carelevo`() {
        assertThat(plugin.manufacturer()).isEqualTo(ManufacturerType.CareMedi)
    }

    @Test
    fun `model should return CARELEVO`() {
        assertThat(plugin.model()).isEqualTo(PumpType.CAREMEDI_CARELEVO)
    }

    @Test
    fun `serialNumber should return manufacture number`() {
        patchInfoSubject.onNext(Optional.of(samplePatchInfo(manufactureNumber = "SN-1234")))

        assertThat(plugin.serialNumber()).isEqualTo("SN-1234")
    }

    // --- #2454: the basal the patch runs ---

    @Test
    fun `the delivered basal is the program the patch accepted rounded as it is sent`() {
        val segments = (0..23).map { CarelevoBasalSegmentInfusionInfoDomainModel(startTime = it, endTime = it + 1, speed = if (it < 6) 0.7905 else 1.0) }
        whenever(setBasalProgramUseCase.acceptedBasalSegments()).thenReturn(segments.shuffled())

        val delivered = plugin.deliveredBasal()!!

        assertThat(delivered.map { it.timeAsSeconds }).containsExactlyElementsIn((0..23).map { it * 3600 }).inOrder()
        assertThat(delivered.map { it.value }).containsExactlyElementsIn((0..23).map { if (it < 6) 0.79 else 1.0 }).inOrder()
    }

    /**
     * Seen on the Pixel: right after a set the infusion-info stream still held the old program, and the report said
     * 1.02 U/h where the patch had just accepted 1.14. The report reads the store, which the write updates at once.
     */
    @Test
    fun `the delivered basal does not come from the infusion info stream`() {
        val old = (0..23).map { CarelevoBasalSegmentInfusionInfoDomainModel(startTime = it, endTime = it + 1, speed = 1.02) }
        val accepted = (0..23).map { CarelevoBasalSegmentInfusionInfoDomainModel(startTime = it, endTime = it + 1, speed = 1.14) }
        infusionInfoSubject.onNext(
            Optional.of(CarelevoInfusionInfoDomainModel(basalInfusionInfo = CarelevoBasalInfusionInfoDomainModel("id", "address", 1, segments = old, isStop = false)))
        )
        whenever(setBasalProgramUseCase.acceptedBasalSegments()).thenReturn(accepted)

        assertThat(plugin.deliveredBasal()!!.map { it.value }.distinct()).containsExactly(1.14)
    }

    /** The base basal is what the patch gives in this hour, the same rate the running profile holds. */
    @Test
    fun `the base basal is the delivered rate of this hour`() {
        val segments = (0..23).map { CarelevoBasalSegmentInfusionInfoDomainModel(startTime = it, endTime = it + 1, speed = 0.7905) }
        whenever(setBasalProgramUseCase.acceptedBasalSegments()).thenReturn(segments)

        assertThat(plugin.baseBasalRate.cU).isEqualTo(0.79)
    }

    @Test
    fun `the delivered basal is unknown before the patch accepted a program`() {
        whenever(setBasalProgramUseCase.acceptedBasalSegments()).thenReturn(null)

        assertThat(plugin.deliveredBasal()).isNull()
    }

    @Test
    fun `isInitialized should return false when patch address is missing`() {
        patchInfoSubject.onNext(Optional.empty())

        assertThat(plugin.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized should stay true when BLE is disconnected`() {
        // Activation-based (like Omnipod Dash / Medtrum): per-op sessions mean there is no resting
        // link, so a down link must NOT report the pump as un-initialized (that would abort the loop's
        // TBR/SMB enact before a command can be queued).
        btStateSubject.onNext(Optional.empty())

        assertThat(plugin.isInitialized()).isTrue()
    }

    @Test
    fun `isInitialized should return false when operational state is missing`() {
        patchInfoSubject.onNext(Optional.of(samplePatchInfo().copy(mode = null, runningMinutes = null, pumpState = null)))

        assertThat(plugin.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized should return true when patch is paired and operational state exists`() {
        assertThat(plugin.isInitialized()).isTrue()
    }

    @Test
    fun `isConnected should return true when patch address is missing`() {
        patchInfoSubject.onNext(Optional.empty())

        assertThat(plugin.isConnected()).isTrue()
    }

    @Test
    fun `isConnected reflects the held link once the patch is activated`() {
        patchInfoSubject.onNext(Optional.of(samplePatchInfo(address = "11:22:33:44:55:66")))

        // Post-activation the queue owns a real link, so isConnected mirrors the held-link state (no
        // longer hardcoded true): down → false so connect-before-execute dials first, up → true.
        whenever(bleSession.connected).thenReturn(MutableStateFlow(false))
        assertThat(plugin.isConnected()).isFalse()

        whenever(bleSession.connected).thenReturn(MutableStateFlow(true))
        assertThat(plugin.isConnected()).isTrue()
    }

    @Test
    fun `isSuspended should reflect patch isStopped flag not the BLE link state`() {
        // Real delivery-suspend (pump stopped by the user), independent of connection: a normal idle
        // disconnect must NOT read as suspended (that surfaced as a false error/suspended icon before).
        patchInfoSubject.onNext(Optional.of(samplePatchInfo().copy(isStopped = true)))
        assertThat(plugin.isSuspended()).isTrue()

        patchInfoSubject.onNext(Optional.of(samplePatchInfo().copy(isStopped = false)))
        assertThat(plugin.isSuspended()).isFalse()
    }

    @Test
    fun `isBusy should always return false`() {
        assertThat(plugin.isBusy()).isFalse()
    }

    @Test
    fun `isConnecting delegates to the session`() {
        whenever(bleSession.isConnecting).thenReturn(MutableStateFlow(true))
        assertThat(plugin.isConnecting()).isTrue()
    }

    @Test
    fun `isHandshakeInProgress should always return false`() {
        assertThat(plugin.isHandshakeInProgress()).isFalse()
    }

    @Test
    fun `baseBasalRate should return zero when profile is missing`() {
        profileSubject.onNext(Optional.empty())

        assertThat(plugin.baseBasalRate.cU).isWithin(0.001).of(0.0)
    }

    @Test
    fun `reservoirLevel should default to zero before observers update state`() {
        patchInfoSubject.onNext(Optional.of(samplePatchInfo(insulinRemain = 42.5)))

        assertThat(plugin.reservoirLevel.value.cU).isWithin(0.001).of(0.0)
    }

    @Test
    fun `batteryLevel should default to null before observers update state`() {
        assertThat(plugin.batteryLevel.value).isNull()
    }

    @Test
    fun `isFakingTempsByExtendedBoluses should return false`() {
        assertThat(plugin.isFakingTempsByExtendedBoluses).isFalse()
    }

    @Test
    fun `canHandleDST should return false`() {
        assertThat(plugin.canHandleDST()).isFalse()
    }

    @Test
    fun `loadTDDs should return result object`() {
        assertThat(runBlocking { plugin.loadTDDs() }).isNotNull()
    }

    @Test
    fun `pumpDescription should be initialized`() {
        assertThat(plugin.pumpDescription).isNotNull()
    }

    @Test
    fun `plugin type should be PUMP`() {
        assertThat(plugin.getType()).isEqualTo(PluginType.PUMP)
    }
}
