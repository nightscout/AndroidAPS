package app.aaps.cgm.dexcomg7.ui

import android.content.Context
import android.content.pm.PackageManager
import app.aaps.cgm.dexcomg7.data.G7State
import app.aaps.cgm.dexcomg7.data.G7StateStore
import app.aaps.cgm.dexcomg7.session.G7PairingService
import app.aaps.cgm.dexcomg7.session.G7PairingState
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class G7PairingViewModelTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var packageManager: PackageManager
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var pairing: G7PairingService
    @Mock lateinit var store: G7StateStore

    private val pairingState = MutableStateFlow<G7PairingState>(G7PairingState.Idle)
    private lateinit var viewModel: G7PairingViewModel

    private val gs = "\u001D"

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(packageManager.getPackageInfo(any<String>(), any<Int>())).thenThrow(PackageManager.NameNotFoundException())
        whenever(rh.gs(any<Int>(), anyVararg<Any>())).thenReturn("text")
        whenever(rh.gs(any<Int>())).thenReturn("text")
        whenever(pairing.state).thenReturn(pairingState)
        whenever(store.value).thenReturn(G7State())
        viewModel = G7PairingViewModel(context, rh, pairing, store)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun scannedApplicatorFillsCodeAndSerial() {
        viewModel.openScanner()
        val handled = viewModel.onBarcode("0100386270001863" + "17260531" + "10LOT42" + gs + "21123456789012" + gs + "2401155")
        assertThat(handled).isTrue()
        val ui = viewModel.uiState.value
        assertThat(ui.step).isEqualTo(G7PairingStep.CODE)
        assertThat(ui.code).isEqualTo("1155")
        assertThat(ui.serial).isEqualTo("123456789012")
        assertThat(ui.codeIsValid).isTrue()
    }

    @Test
    fun otherBarcodesKeepTheScannerOpen() {
        viewModel.openScanner()
        assertThat(viewModel.onBarcode("https://example.com")).isFalse()
        assertThat(viewModel.onBarcode("0100386270001863")).isFalse()
        assertThat(viewModel.uiState.value.step).isEqualTo(G7PairingStep.SCANNER)
    }

    @Test
    fun typedCodeIsCleanedAndDropsTheSerial() {
        viewModel.onBarcode("21123456789012" + gs + "2401155")
        viewModel.updateCode("12a345")
        assertThat(viewModel.uiState.value.code).isEqualTo("1234")
        assertThat(viewModel.uiState.value.serial).isNull()
    }

    @Test
    fun pairingStartsOnlyWithAValidCode() {
        viewModel.updateCode("12")
        viewModel.startPairing()
        verify(pairing, never()).start(any(), any())

        viewModel.onBarcode("21123456789012" + gs + "2401155")
        viewModel.startPairing()
        verify(pairing).start("1155", "123456789012")
        assertThat(viewModel.uiState.value.step).isEqualTo(G7PairingStep.PAIRING)
    }

    @Test
    fun followsThePairingRun() {
        pairingState.value = G7PairingState.Succeeded("DXCM12")
        assertThat(viewModel.uiState.value.step).isEqualTo(G7PairingStep.DONE)
        pairingState.value = G7PairingState.Failed(G7PairingState.Reason.NO_SENSOR_FOUND)
        assertThat(viewModel.uiState.value.step).isEqualTo(G7PairingStep.FAILED)
        assertThat(viewModel.uiState.value.failureText).isNotNull()
    }
}
