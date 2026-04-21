package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.RequestResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchRepository
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CarelevoRequestPatchInfusionInfoUseCaseTest {

    private val patchRepository: CarelevoPatchRepository = mock()
    private val sut = CarelevoRequestPatchInfusionInfoUseCase(patchRepository)

    @Test
    fun execute_returns_success_when_request_is_pending() {
        whenever(patchRepository.requestRetrieveInfusionStatusInfo(any()))
            .thenReturn(Single.just(RequestResult.Pending(true)))

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun execute_returns_error_when_request_is_not_pending() {
        whenever(patchRepository.requestRetrieveInfusionStatusInfo(any()))
            .thenReturn(Single.just(RequestResult.Success(true)))

        val result = sut.execute().blockingGet()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }
}
