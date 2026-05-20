package app.aaps.pump.carelevo

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.pump.carelevo.common.model.PatchState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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

    @Test
    fun `isInitialized should return false when patch address is missing`() {
        patchInfoSubject.onNext(Optional.empty())

        assertThat(plugin.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized should return false when BLE is disconnected`() {
        whenever(carelevoPatch.isBleConnectedNow(any())).thenReturn(false)

        assertThat(plugin.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized should return false when operational state is missing`() {
        patchInfoSubject.onNext(Optional.of(samplePatchInfo().copy(mode = null, runningMinutes = null, pumpState = null)))
        whenever(carelevoPatch.isBleConnectedNow(any())).thenReturn(true)

        assertThat(plugin.isInitialized()).isFalse()
    }

    @Test
    fun `isInitialized should return true when BLE is connected and operational state exists`() {
        assertThat(plugin.isInitialized()).isTrue()
    }

    @Test
    fun `isConnected should return true when patch address is missing`() {
        patchInfoSubject.onNext(Optional.empty())

        assertThat(plugin.isConnected()).isTrue()
    }

    @Test
    fun `isConnected should reflect BLE connection when address exists`() {
        patchInfoSubject.onNext(Optional.of(samplePatchInfo(address = "11:22:33:44:55:66")))
        whenever(carelevoPatch.isBleConnectedNow("11:22:33:44:55:66")).thenReturn(true)

        assertThat(plugin.isConnected()).isTrue()
    }

    @Test
    fun `isSuspended should be true only for NotConnectedBooted`() {
        whenever(carelevoPatch.resolvePatchState()).thenReturn(PatchState.NotConnectedBooted)

        assertThat(plugin.isSuspended()).isTrue()
    }

    @Test
    fun `isBusy should always return false`() {
        assertThat(plugin.isBusy()).isFalse()
    }

    @Test
    fun `isConnecting should always return false`() {
        assertThat(plugin.isConnecting()).isFalse()
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
        assertThat(plugin.loadTDDs()).isNotNull()
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
