package app.aaps.pump.carelevo

import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CarelevoPumpPluginStatusTest : CarelevoPumpPluginTestBase() {

    @Test
    fun `connect should not throw and should keep plugin usable`() {
        plugin.connect("test")

        assertThat(plugin).isNotNull()
    }

    @Test
    fun `disconnect should not throw`() {
        plugin.disconnect("test")

        assertThat(plugin).isNotNull()
    }

    @Test
    fun `stopConnecting should not throw`() {
        plugin.stopConnecting()

        assertThat(plugin).isNotNull()
    }

    @Test
    fun `getPumpStatus should skip request when bluetooth is disabled`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        plugin.getPumpStatus("test")

        verify(requestPatchInfusionInfoUseCase, never()).execute()
    }

    @Test
    fun `getPumpStatus should skip request when pump is disconnected`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(true)
        whenever(carelevoPatch.isCarelevoConnected()).thenReturn(false)

        plugin.getPumpStatus("test")

        verify(requestPatchInfusionInfoUseCase, never()).execute()
    }

    @Test
    fun `getPumpStatus should request infusion info when connected`() {
        whenever(requestPatchInfusionInfoUseCase.execute()).thenReturn(
            Single.just(ResponseResult.Success(ResultSuccess))
        )

        plugin.getPumpStatus("test")

        verify(requestPatchInfusionInfoUseCase).execute()
    }

    @Test
    fun `timezoneOrDSTChanged should call timezone update use case`() {
        whenever(carelevoPatchTimeZoneUpdateUseCase.execute(any())).thenReturn(
            Single.just(ResponseResult.Success(ResultSuccess))
        )

        plugin.timezoneOrDSTChanged(TimeChangeType.TimezoneChanged)

        verify(carelevoPatchTimeZoneUpdateUseCase).execute(any())
    }
}
