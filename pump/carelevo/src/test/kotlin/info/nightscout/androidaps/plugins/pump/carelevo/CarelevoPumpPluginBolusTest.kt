package info.nightscout.androidaps.plugins.pump.carelevo

import app.aaps.core.interfaces.pump.DetailedBolusInfo
import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.result.ResultSuccess
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.Optional

class CarelevoPumpPluginBolusTest : CarelevoPumpPluginTestBase() {

    @Test
    fun `deliverTreatment should require insulin greater than zero`() {
        val bolusInfo = DetailedBolusInfo().apply {
            insulin = 0.0
            carbs = 0.0
        }

        assertThrows(IllegalArgumentException::class.java) {
            plugin.deliverTreatment(bolusInfo)
        }
    }

    @Test
    fun `deliverTreatment should require carbs equal to zero`() {
        val bolusInfo = DetailedBolusInfo().apply {
            insulin = 1.0
            carbs = 10.0
        }

        assertThrows(IllegalArgumentException::class.java) {
            plugin.deliverTreatment(bolusInfo)
        }
    }

    @Test
    fun `deliverTreatment should return not enacted when bluetooth is disabled`() {
        whenever(carelevoPatch.isBluetoothEnabled()).thenReturn(false)

        val result = plugin.deliverTreatment(DetailedBolusInfo().apply {
            insulin = 1.0
            carbs = 0.0
        })

        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `deliverTreatment should return not enacted when pump is disconnected`() {
        whenever(carelevoPatch.isCarelevoConnected()).thenReturn(false)

        val result = plugin.deliverTreatment(DetailedBolusInfo().apply {
            insulin = 1.0
            carbs = 0.0
        })

        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `deliverTreatment should reject when immediate bolus is already running`() {
        infusionInfoSubject.onNext(
            Optional.of(
                CarelevoInfusionInfoDomainModel(
                    immeBolusInfusionInfo = CarelevoImmeBolusInfusionInfoDomainModel(
                        infusionId = "imme-1",
                        address = "AA:BB:CC:DD:EE:FF",
                        mode = 3,
                        volume = 1.0,
                        infusionDurationSeconds = 30
                    )
                )
            )
        )

        val result = plugin.deliverTreatment(DetailedBolusInfo().apply {
            insulin = 1.0
            carbs = 0.0
        })

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
        assertThat(result.bolusDelivered).isWithin(0.001).of(0.0)
    }

    @Test
    fun `setExtendedBolus should succeed when use case succeeds`() {
        whenever(startExtendBolusInfusionUseCase.execute(any())).thenReturn(
            Single.just(ResponseResult.Success(ResultSuccess))
        )

        val result = plugin.setExtendedBolus(insulin = 1.2, durationInMinutes = 30)

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
    }

    @Test
    fun `setExtendedBolus should fail when use case returns error`() {
        whenever(startExtendBolusInfusionUseCase.execute(any())).thenReturn(
            Single.just(ResponseResult.Error(IllegalStateException("failed")))
        )

        val result = plugin.setExtendedBolus(insulin = 1.2, durationInMinutes = 30)

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun `cancelExtendedBolus should succeed when use case succeeds`() {
        whenever(cancelExtendBolusInfusionUseCase.execute()).thenReturn(
            Single.just(ResponseResult.Success(ResultSuccess))
        )

        val result = plugin.cancelExtendedBolus()

        assertThat(result.success).isTrue()
        assertThat(result.enacted).isTrue()
        assertThat(result.isTempCancel).isTrue()
    }

    @Test
    fun `cancelExtendedBolus should fail when use case returns error`() {
        whenever(cancelExtendBolusInfusionUseCase.execute()).thenReturn(
            Single.just(ResponseResult.Error(IllegalStateException("failed")))
        )

        val result = plugin.cancelExtendedBolus()

        assertThat(result.success).isFalse()
        assertThat(result.enacted).isFalse()
    }
}
