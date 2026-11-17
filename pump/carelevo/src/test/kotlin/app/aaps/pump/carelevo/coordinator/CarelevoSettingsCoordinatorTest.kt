package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoDeleteUserSettingInfoUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoSettingsCoordinatorTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var deleteUserSettingInfoUseCase: CarelevoDeleteUserSettingInfoUseCase

    private lateinit var sut: CarelevoSettingsCoordinator
    private lateinit var scope: CoroutineScope

    private fun stubDeleteResult(result: ResponseResult<CarelevoUseCaseResponse>) {
        whenever { deleteUserSettingInfoUseCase.execute() }.thenReturn(result)
    }

    @BeforeEach
    fun setUp() {
        // An unconfined scope runs the delete and its log on the test thread, before
        // clearUserSettings() returns.
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        sut = CarelevoSettingsCoordinator(aapsLogger, deleteUserSettingInfoUseCase)
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `clearUserSettings logs success when the delete succeeds`() {
        stubDeleteResult(ResponseResult.Success(ResultSuccess))

        sut.clearUserSettings(scope)

        verifyBlocking(deleteUserSettingInfoUseCase) { execute() }
        verify(aapsLogger).debug(LTag.PUMPCOMM, "deleteUserSettingInfo.success")
    }

    @Test
    fun `clearUserSettings logs the response error when the delete returns Error`() {
        val boom = RuntimeException("delete failed")
        stubDeleteResult(ResponseResult.Error(boom))

        sut.clearUserSettings(scope)

        verify(aapsLogger).debug(LTag.PUMPCOMM, "deleteUserSettingInfo.responseError error=$boom")
    }

    @Test
    fun `clearUserSettings logs a generic failure when the delete returns Failure`() {
        stubDeleteResult(ResponseResult.Failure("nope"))

        sut.clearUserSettings(scope)

        verify(aapsLogger).debug(LTag.PUMPCOMM, "deleteUserSettingInfo.failure")
    }

    @Test
    fun `clearUserSettings runs the delete through the use case`() {
        stubDeleteResult(ResponseResult.Success(ResultSuccess))

        sut.clearUserSettings(scope)

        verifyBlocking(deleteUserSettingInfoUseCase) { execute() }
    }
}
