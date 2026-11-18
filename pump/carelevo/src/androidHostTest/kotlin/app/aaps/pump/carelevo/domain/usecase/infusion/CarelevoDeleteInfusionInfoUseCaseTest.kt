package app.aaps.pump.carelevo.domain.usecase.infusion

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoPatchMode
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.infusion.model.CarelevoDeleteInfusionRequestModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Clock

internal class CarelevoDeleteInfusionInfoUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()
    private val sut = CarelevoDeleteInfusionInfoUseCase(patchInfoRepository, infusionInfoRepository)

    private val patchInfo = CarelevoPatchInfoDomainModel("AA:BB", Clock.System.now(), Clock.System.now(), mode = 1)

    private fun request(tempBasal: Boolean = false, immeBolus: Boolean = false, extendBolus: Boolean = false) =
        CarelevoDeleteInfusionRequestModel(isDeleteTempBasal = tempBasal, isDeleteImmeBolus = immeBolus, isDeleteExtendBolus = extendBolus)

    private fun errorMessage(result: ResponseResult<*>): String? = (result as ResponseResult.Error).e.message

    @Test
    fun execute_returns_success_when_delete_and_patch_update_succeed() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel())
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = runBlocking { sut.execute(request(tempBasal = true)) }

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun `it deletes only what the request names and stores the mode of what is left`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.deleteExtendBolusInfusionInfo()).thenReturn(true)
        val tempBasal = CarelevoTempBasalInfusionInfoDomainModel(infusionId = "t", address = "AA:BB", mode = CarelevoPatchMode.TEMP_BASAL)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(tempBasalInfusionInfo = tempBasal))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = runBlocking { sut.execute(request(immeBolus = true, extendBolus = true)) }

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
        verify(infusionInfoRepository, never()).deleteTempBasalInfusionInfo()
        verify(infusionInfoRepository).deleteImmeBolusInfusionInfo()
        verify(infusionInfoRepository).deleteExtendBolusInfusionInfo()
        val stored = argumentCaptor<CarelevoPatchInfoDomainModel>().also { verify(patchInfoRepository).updatePatchInfo(it.capture()) }.firstValue
        assertThat(stored.mode).isEqualTo(CarelevoPatchMode.TEMP_BASAL)
        assertThat(stored.address).isEqualTo("AA:BB")
    }

    @Test
    fun `with nothing left running the patch is stored as stopped`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel())
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        runBlocking { sut.execute(request()) }

        val stored = argumentCaptor<CarelevoPatchInfoDomainModel>().also { verify(patchInfoRepository).updatePatchInfo(it.capture()) }.firstValue
        assertThat(stored.mode).isEqualTo(CarelevoPatchMode.BASAL_STOPPED)
    }

    @Test
    fun `a running basal is stored as running`() {
        val basal = CarelevoBasalInfusionInfoDomainModel(infusionId = "b", address = "AA:BB", mode = 1, segments = emptyList(), isStop = false)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basal))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        runBlocking { sut.execute(request()) }

        val stored = argumentCaptor<CarelevoPatchInfoDomainModel>().also { verify(patchInfoRepository).updatePatchInfo(it.capture()) }.firstValue
        assertThat(stored.mode).isEqualTo(CarelevoPatchMode.BASAL_RUNNING)
    }

    @Test
    fun `a failed temp basal delete is an error`() {
        whenever(infusionInfoRepository.deleteTempBasalInfusionInfo()).thenReturn(false)

        val result = runBlocking { sut.execute(request(tempBasal = true)) }

        assertThat(errorMessage(result)).isEqualTo("Failed to delete temp basal infusion info")
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun `a failed immediate bolus delete is an error`() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(false)

        val result = runBlocking { sut.execute(request(immeBolus = true)) }

        assertThat(errorMessage(result)).isEqualTo("Failed to delete immediate bolus infusion info")
    }

    @Test
    fun `a failed extended bolus delete is an error`() {
        whenever(infusionInfoRepository.deleteExtendBolusInfusionInfo()).thenReturn(false)

        val result = runBlocking { sut.execute(request(extendBolus = true)) }

        assertThat(errorMessage(result)).isEqualTo("Failed to delete extended bolus infusion info")
    }

    @Test
    fun `no infusion info after the delete is an error`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(null)

        val result = runBlocking { sut.execute(request()) }

        assertThat(errorMessage(result)).isEqualTo("Infusion info must not be null after deletion step")
    }

    @Test
    fun `no patch info is an error`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel())
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(null)

        val result = runBlocking { sut.execute(request()) }

        assertThat(errorMessage(result)).isEqualTo("Patch info must not be null")
    }

    @Test
    fun `a failed patch info update is an error`() {
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel())
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        val result = runBlocking { sut.execute(request()) }

        assertThat(errorMessage(result)).isEqualTo("Failed to update patch info (mode=0)")
    }

    @Test
    fun `a request of another type is an error`() {
        val result = runBlocking { sut.execute(object : CarelevoUseCaseRequest {}) }

        assertThat(errorMessage(result)).isEqualTo("Request must be CarelevoDeleteInfusionRequestModel")
        verify(infusionInfoRepository, never()).getInfusionInfoBySync()
    }
}
