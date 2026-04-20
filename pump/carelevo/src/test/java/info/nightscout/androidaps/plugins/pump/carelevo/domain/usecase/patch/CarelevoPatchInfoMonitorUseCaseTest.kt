package info.nightscout.androidaps.plugins.pump.carelevo.domain.usecase.patch

import com.google.common.truth.Truth.assertThat
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.ResponseResult
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import io.reactivex.rxjava3.core.Observable
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

internal class CarelevoPatchInfoMonitorUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val sut = CarelevoPatchInfoMonitorUseCase(patchInfoRepository)

    @Test
    fun execute_maps_repository_values_to_success() {
        val patchInfo = CarelevoPatchInfoDomainModel(
            address = "94:b2:16:1d:2f:6d",
            createdAt = DateTime.now().minusHours(1),
            updatedAt = DateTime.now()
        )
        whenever(patchInfoRepository.getPatchInfo())
            .thenReturn(Observable.just(Optional.of(patchInfo)))

        val result = sut.execute().blockingFirst()

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun execute_returns_error_when_repository_throws() {
        whenever(patchInfoRepository.getPatchInfo()).thenThrow(IllegalStateException("db fail"))

        val result = sut.execute().blockingFirst()

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }
}
