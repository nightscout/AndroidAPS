package app.aaps.ui.compose.eversenseCalibrationDialog

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.source.BgSource
import app.aaps.core.interfaces.source.EversenseCalibrationSource
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class EversenseCalibrationDialogViewModelTest {
    private interface CalibratingSource : BgSource, EversenseCalibrationSource
    private val profile: ProfileUtil = mock()
    private val source: CalibratingSource = mock()
    private val active: ActivePlugin = mock()
    private val logger: UserEntryLogger = mock()

    @BeforeEach fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(profile.units).thenReturn(GlucoseUnit.MGDL)
        whenever(active.activeBgSource).thenReturn(source)
        whenever(source.isEnabled()).thenReturn(true)
        whenever(source.isConnected()).thenReturn(true)
    }
    @AfterEach fun cleanup() = Dispatchers.resetMain()

    @Test fun invalidInputCannotReachTransmitter() = runTest {
        val vm = EversenseCalibrationDialogViewModel(profile, active, logger, generatedTextResolver(), mock())
        for (value in listOf(0.0, -1.0, 39.0, 401.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            vm.updateBg(value)
            assertThat(vm.uiState.value.canSubmit).isFalse()
            vm.submit()
        }
        advanceUntilIdle()
        verify(source, never()).calibrate(any())
    }

    @Test fun convertsMmolAndSendsOnlyOnceWhileSubmitting() = runTest {
        whenever(profile.units).thenReturn(GlucoseUnit.MMOL)
        whenever(profile.convertToMgdl(5.5, GlucoseUnit.MMOL)).thenReturn(99.0)
        whenever(source.calibrate(99)).thenReturn(true)
        val vm = EversenseCalibrationDialogViewModel(profile, active, logger, generatedTextResolver(), mock())
        vm.updateBg(5.5)
        vm.submit()
        vm.submit()
        advanceUntilIdle()
        verify(source).calibrate(99)
        assertThat(vm.uiState.value.submitting).isFalse()
    }

    @Test fun sourceSwitchBeforeSubmitPreventsSendingToInactiveTransmitter() = runTest {
        val vm = EversenseCalibrationDialogViewModel(profile, active, logger, generatedTextResolver(), mock())
        whenever(active.activeBgSource).thenReturn(mock<BgSource>())
        whenever(profile.convertToMgdl(100.0, GlucoseUnit.MGDL)).thenReturn(100.0)
        vm.updateBg(100.0)
        vm.submit()
        advanceUntilIdle()
        verify(source, never()).calibrate(any())
        assertThat(vm.uiState.value.submitting).isFalse()
    }
    @Test fun rapidChangeWarningDoesNotBlockSubmission() = runTest {
        val dynamics: EversenseCalibrationDynamics = mock()
        whenever(dynamics.hasRapidChange()).thenReturn(true)
        whenever(profile.convertToMgdl(100.0, GlucoseUnit.MGDL)).thenReturn(100.0)
        whenever(source.calibrate(100)).thenReturn(true)
        val vm = EversenseCalibrationDialogViewModel(profile, active, logger, generatedTextResolver(), dynamics)
        assertThat(vm.dynamics.hasRapidChange()).isTrue()
        vm.updateBg(100.0)
        assertThat(vm.uiState.value.canSubmit).isTrue()
        vm.submit()
        advanceUntilIdle()
        verify(source).calibrate(100)
    }

}
