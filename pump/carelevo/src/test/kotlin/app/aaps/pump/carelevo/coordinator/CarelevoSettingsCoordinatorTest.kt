package app.aaps.pump.carelevo.coordinator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.carelevo.domain.model.ResponseResult
import app.aaps.pump.carelevo.domain.model.result.ResultSuccess
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import app.aaps.pump.carelevo.domain.usecase.userSetting.CarelevoDeleteUserSettingInfoUseCase
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class CarelevoSettingsCoordinatorTest {

    @Mock lateinit var aapsLogger: AAPSLogger
    @Mock lateinit var aapsSchedulers: AapsSchedulers
    @Mock lateinit var deleteUserSettingInfoUseCase: CarelevoDeleteUserSettingInfoUseCase

    private lateinit var sut: CarelevoSettingsCoordinator
    private lateinit var disposable: CompositeDisposable

    private fun stubDeleteResult(result: ResponseResult<CarelevoUseCaseResponse>) {
        whenever(deleteUserSettingInfoUseCase.execute()).thenReturn(Single.just(result))
    }

    @BeforeEach
    fun setUp() {
        // Trampoline so the whole Rx chain (subscribeOn/observeOn) runs synchronously in-thread and
        // the subscribe { } lambda has fired before clearUserSettings() returns.
        whenever(aapsSchedulers.io).thenReturn(Schedulers.trampoline())
        disposable = CompositeDisposable()
        sut = CarelevoSettingsCoordinator(aapsLogger, aapsSchedulers, deleteUserSettingInfoUseCase)
    }

    @Test
    fun `clearUserSettings logs success when the delete succeeds`() {
        stubDeleteResult(ResponseResult.Success(ResultSuccess))

        sut.clearUserSettings(disposable)

        verify(deleteUserSettingInfoUseCase).execute()
        verify(aapsLogger).debug(LTag.PUMPCOMM, "deleteUserSettingInfo.success")
    }

    @Test
    fun `clearUserSettings logs the response error when the delete returns Error`() {
        val boom = RuntimeException("delete failed")
        stubDeleteResult(ResponseResult.Error(boom))

        sut.clearUserSettings(disposable)

        verify(aapsLogger).debug(LTag.PUMPCOMM, "deleteUserSettingInfo.responseError error=$boom")
    }

    @Test
    fun `clearUserSettings logs a generic failure when the delete returns Failure`() {
        stubDeleteResult(ResponseResult.Failure("nope"))

        sut.clearUserSettings(disposable)

        verify(aapsLogger).debug(LTag.PUMPCOMM, "deleteUserSettingInfo.failure")
    }

    @Test
    fun `clearUserSettings subscribes through the use case`() {
        stubDeleteResult(ResponseResult.Success(ResultSuccess))

        sut.clearUserSettings(disposable)

        verify(deleteUserSettingInfoUseCase).execute()
    }
}
