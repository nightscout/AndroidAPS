package app.aaps.pump.carelevo

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class CarelevoPumpPluginTempBasalTest : CarelevoPumpPluginTestBase() {

    @Test
    fun `setTempBasalAbsolute should return not enacted when bluetooth is disabled`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        val result = plugin.setTempBasalAbsolute(1.2, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `setTempBasalAbsolute should return not enacted when pump is disconnected`() {
        whenever(carelevoPatch.isCarelevoConnected()).thenReturn(false)

        val result = plugin.setTempBasalAbsolute(1.2, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `setTempBasalAbsolute should succeed on success response`() {
        whenever(startTempBasalInfusionUseCase.execute(any())).thenReturn(
            Single.just(ResponseResult.Success(ResultSuccess))
        )

        val result = plugin.setTempBasalAbsolute(1.2, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.absolute).isWithin(0.001).of(1.2)
    }

    @Test
    fun `setTempBasalAbsolute should fail on error response`() {
        whenever(startTempBasalInfusionUseCase.execute(any())).thenReturn(
            Single.just(ResponseResult.Error(IllegalStateException("failed")))
        )

        val result = plugin.setTempBasalAbsolute(1.2, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `setTempBasalPercent should succeed on success response`() {
        whenever(startTempBasalInfusionUseCase.execute(any())).thenReturn(
            Single.just(ResponseResult.Success(ResultSuccess))
        )

        val result = plugin.setTempBasalPercent(150, 30, false, PumpSync.TemporaryBasalType.NORMAL)

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.percent).isEqualTo(150)
    }

    @Test
    fun `setTempBasalPercent should fail when use case errors`() {
        whenever(startTempBasalInfusionUseCase.execute(any())).thenReturn(
            Single.error(IllegalStateException("timeout"))
        )

        assertThrows(IllegalStateException::class.java) {
            plugin.setTempBasalPercent(150, 30, false, PumpSync.TemporaryBasalType.NORMAL)
        }
    }

    @Test
    fun `cancelTempBasal should return not enacted when disconnected`() {
        whenever(carelevoPatch.isCarelevoConnected()).thenReturn(false)

        val result = plugin.cancelTempBasal(false)

        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `cancelTempBasal should succeed on success response`() {
        whenever(cancelTempBasalInfusionUseCase.execute()).thenReturn(
            Single.just(ResponseResult.Success(ResultSuccess))
        )

        val result = plugin.cancelTempBasal(false)

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.isTempCancel).isTrue()
    }

    @Test
    fun `cancelTempBasal should return success false and enacted false on timeout`() {
        whenever(cancelTempBasalInfusionUseCase.execute()).thenReturn(
            Single.error(IllegalStateException("timeout"))
        )

        val result = plugin.cancelTempBasal(false)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }
}
