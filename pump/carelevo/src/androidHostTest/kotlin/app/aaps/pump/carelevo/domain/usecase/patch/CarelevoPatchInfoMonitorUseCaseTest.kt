package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

internal class CarelevoPatchInfoMonitorUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()
    private val sut = CarelevoPatchInfoMonitorUseCase(patchInfoRepository)

    @Test
    fun execute_maps_repository_values_to_success() {
        val patchInfo = CarelevoPatchInfoDomainModel(
            address = "94:b2:16:1d:2f:6d",
            createdAt = Clock.System.now() - 1.hours,
            updatedAt = Clock.System.now()
        )
        whenever(patchInfoRepository.getPatchInfo())
            .thenReturn(flowOf(patchInfo))

        val result = runBlocking { sut.execute().first() }

        assertThat(result).isInstanceOf(ResponseResult.Success::class.java)
    }

    @Test
    fun execute_returns_error_when_repository_throws() {
        whenever(patchInfoRepository.getPatchInfo()).thenThrow(IllegalStateException("db fail"))

        val result = runBlocking { sut.execute().first() }

        assertThat(result).isInstanceOf(ResponseResult.Error::class.java)
    }
}
