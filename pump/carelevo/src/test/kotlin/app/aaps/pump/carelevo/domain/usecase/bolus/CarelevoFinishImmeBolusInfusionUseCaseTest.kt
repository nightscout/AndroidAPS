package app.aaps.pump.carelevo.domain.usecase.bolus

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalSegmentInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class CarelevoFinishImmeBolusInfusionUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val infusionInfoRepository: CarelevoInfusionInfoRepository = mock()

    private val sut = CarelevoFinishImmeBolusInfusionUseCase(
        patchInfoRepository,
        infusionInfoRepository
    )

    @Test
    fun execute_returns_success_when_cleanup_succeeds() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(
            CarelevoInfusionInfoDomainModel(
                basalInfusionInfo = CarelevoBasalInfusionInfoDomainModel(
                    infusionId = "basal-1",
                    address = "AA:BB",
                    mode = 1,
                    segments = listOf(CarelevoBasalSegmentInfusionInfoDomainModel(startTime = 0, endTime = 1, speed = 1.0)),
                    isStop = false
                )
            )
        )
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(CarelevoPatchInfoDomainModel("AA:BB", DateTime.now(), DateTime.now(), mode = 1))
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    // ---------- helpers ----------

    private fun patchInfo(address: String = "AA:BB"): CarelevoPatchInfoDomainModel =
        CarelevoPatchInfoDomainModel(address, DateTime.now(), DateTime.now(), mode = 3)

    private fun basalInfusion(isStop: Boolean): CarelevoBasalInfusionInfoDomainModel =
        CarelevoBasalInfusionInfoDomainModel(
            infusionId = "basal-1",
            address = "AA:BB",
            mode = 1,
            segments = listOf(CarelevoBasalSegmentInfusionInfoDomainModel(startTime = 0, endTime = 1, speed = 1.0)),
            isStop = isStop
        )

    /** Stub the whole happy path, leaving the caller to override the one step under test. */
    private fun stubHappyPath(infusionInfo: CarelevoInfusionInfoDomainModel = CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false))) {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(infusionInfo)
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(patchInfo())
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)
    }

    private fun capturedPatch(): CarelevoPatchInfoDomainModel {
        val captor = argumentCaptor<CarelevoPatchInfoDomainModel>()
        verify(patchInfoRepository).updatePatchInfo(captor.capture())
        return captor.firstValue
    }

    private fun executeExpectingError(): Throwable {
        val result = sut.execute().blockingGet()
        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
        return (result as ResponseResult.Error).e
    }

    // ---------- failure branches ----------

    @Test
    fun execute_returns_error_when_delete_imme_bolus_fails_and_never_reads_infusion_info() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(false)

        assertThat(executeExpectingError()).isInstanceOf(IllegalStateException::class.java)
        verify(infusionInfoRepository, never()).getInfusionInfoBySync()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun execute_returns_error_when_infusion_info_is_missing() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(null)

        assertThat(executeExpectingError()).isInstanceOf(NullPointerException::class.java)
        verify(patchInfoRepository, never()).getPatchInfoBySync()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun execute_returns_error_when_patch_info_is_missing() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(null)

        assertThat(executeExpectingError()).isInstanceOf(NullPointerException::class.java)
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun execute_returns_error_when_derived_mode_is_null_because_no_infusion_records_remain() {
        stubHappyPath(infusionInfo = CarelevoInfusionInfoDomainModel())

        assertThat(executeExpectingError()).isInstanceOf(NullPointerException::class.java)
        // this use case reads the patch BEFORE deriving the mode, so the read still happened
        verify(patchInfoRepository).getPatchInfoBySync()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }

    @Test
    fun execute_returns_error_when_patch_update_fails() {
        stubHappyPath()
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        assertThat(executeExpectingError()).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun execute_returns_error_when_delete_throws() {
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenThrow(RuntimeException("boom"))

        assertThat(executeExpectingError()).hasMessageThat().isEqualTo("boom")
    }

    @Test
    fun execute_returns_error_when_patch_update_throws() {
        stubHappyPath()
        whenever(patchInfoRepository.updatePatchInfo(any())).thenThrow(RuntimeException("boom"))

        assertThat(executeExpectingError()).hasMessageThat().isEqualTo("boom")
    }

    // ---------- success payload + derivePatchMode branches ----------

    @Test
    fun execute_success_carries_ResultSuccess_as_the_payload() {
        stubHappyPath()

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
        assertThat((result as ResponseResult.Success).data).isSameInstanceAs(ResultSuccess)
    }

    @Test
    fun execute_success_derives_BASAL_RUNNING_mode_1_from_a_running_basal() {
        stubHappyPath(infusionInfo = CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false)))

        assertThat(sut.execute().blockingGet()).isInstanceOf(ResponseResult.Success::class.java)
        assertThat(capturedPatch().mode).isEqualTo(1)
    }

    @Test
    fun execute_success_derives_BASAL_STOPPED_mode_0_from_a_stopped_basal() {
        stubHappyPath(infusionInfo = CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = true)))

        assertThat(sut.execute().blockingGet()).isInstanceOf(ResponseResult.Success::class.java)
        assertThat(capturedPatch().mode).isEqualTo(0)
    }

    @Test
    fun execute_success_derives_TEMP_BASAL_mode_2_when_a_temp_basal_is_still_running() {
        stubHappyPath(
            infusionInfo = CarelevoInfusionInfoDomainModel(
                tempBasalInfusionInfo = CarelevoTempBasalInfusionInfoDomainModel(infusionId = "t", address = "AA:BB", mode = 2)
            )
        )

        assertThat(sut.execute().blockingGet()).isInstanceOf(ResponseResult.Success::class.java)
        assertThat(capturedPatch().mode).isEqualTo(2)
    }

    @Test
    fun execute_success_derives_IMME_BOLUS_mode_3_when_an_imme_bolus_record_still_remains() {
        stubHappyPath(
            infusionInfo = CarelevoInfusionInfoDomainModel(
                basalInfusionInfo = basalInfusion(isStop = false),
                immeBolusInfusionInfo = CarelevoImmeBolusInfusionInfoDomainModel(infusionId = "i", address = "AA:BB", mode = 3)
            )
        )

        assertThat(sut.execute().blockingGet()).isInstanceOf(ResponseResult.Success::class.java)
        assertThat(capturedPatch().mode).isEqualTo(3)
    }

    @Test
    fun execute_success_derives_EXTEND_BOLUS_mode_5_when_an_extended_bolus_outranks_everything_else() {
        stubHappyPath(
            infusionInfo = CarelevoInfusionInfoDomainModel(
                basalInfusionInfo = basalInfusion(isStop = false),
                extendBolusInfusionInfo = CarelevoExtendBolusInfusionInfoDomainModel(infusionId = "e", address = "AA:BB", mode = 5)
            )
        )

        assertThat(sut.execute().blockingGet()).isInstanceOf(ResponseResult.Success::class.java)
        assertThat(capturedPatch().mode).isEqualTo(5)
    }

    @Test
    fun execute_success_preserves_the_patch_identity_and_only_rewrites_mode_and_updatedAt() {
        val original = CarelevoPatchInfoDomainModel(
            address = "11:22:33:44:55:66",
            createdAt = DateTime.now().minusDays(1),
            updatedAt = DateTime.now().minusHours(1),
            mode = 3,
            bolusActionSeq = 5,
            insulinRemain = 150.0
        )
        whenever(infusionInfoRepository.deleteImmeBolusInfusionInfo()).thenReturn(true)
        whenever(infusionInfoRepository.getInfusionInfoBySync()).thenReturn(CarelevoInfusionInfoDomainModel(basalInfusionInfo = basalInfusion(isStop = false)))
        whenever(patchInfoRepository.getPatchInfoBySync()).thenReturn(original)
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        assertThat(sut.execute().blockingGet()).isInstanceOf(ResponseResult.Success::class.java)

        val persisted = capturedPatch()
        assertThat(persisted.address).isEqualTo("11:22:33:44:55:66")
        assertThat(persisted.mode).isEqualTo(1)
        assertThat(persisted.createdAt.millis).isEqualTo(original.createdAt.millis)
        // finishing a bolus does not clear the action seq
        assertThat(persisted.bolusActionSeq).isEqualTo(5)
        assertThat(persisted.insulinRemain).isEqualTo(150.0)
        assertThat(persisted.updatedAt.millis).isGreaterThan(original.updatedAt.millis)
    }

    @Test
    fun execute_is_cold_and_does_not_touch_the_repositories_until_subscribed() {
        stubHappyPath()

        sut.execute()

        verify(infusionInfoRepository, never()).deleteImmeBolusInfusionInfo()
        verify(patchInfoRepository, never()).updatePatchInfo(any())
    }
}
