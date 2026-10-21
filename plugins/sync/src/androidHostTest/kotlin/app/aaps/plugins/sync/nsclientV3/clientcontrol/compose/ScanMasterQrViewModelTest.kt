package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientSetupCode
import app.aaps.core.nssdk.localmodel.clientcontrol.PairingPayload
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientPairingRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScanMasterQrViewModelTest {

    @Mock private lateinit var pairingRepository: ClientPairingRepository
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var configBuilder: ConfigBuilder
    @Mock private lateinit var nsClientV3Plugin: NSClientV3Plugin
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var aapsLogger: AAPSLogger

    private lateinit var sut: ScanMasterQrViewModel

    private val now = 1_000_000L
    private val pairing = PairingPayload(masterInstallId = "master-1", clientId = "client-1", secretHex = "ab".repeat(32), expiresAt = now + 60_000L)
    private val nsOnly = ClientSetupCode(url = "https://ns.example", token = "aapstablet-0123456789abcdef")
    private val withPairing = nsOnly.copy(pairing = pairing)

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(dateUtil.now()).thenReturn(now)
        whenever(nsClientV3Plugin.isEnabled()).thenReturn(true)
        sut = ScanMasterQrViewModel(pairingRepository, preferences, configBuilder, { nsClientV3Plugin }, dateUtil, aapsLogger)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a QR code that is not from AAPS is refused`() {
        sut.onScanned("https://example.com")

        assertThat(sut.state.value).isEqualTo(ScanMasterQrViewModel.UiState.Error(ScanMasterQrViewModel.ErrorReason.NotACode))
    }

    @Test
    fun `a code of another version is refused`() {
        sut.onScanned("""{"type":"aaps-client-setup","v":2,"url":"https://ns.example","token":"t-0123456789abcdef"}""")

        assertThat(sut.state.value).isEqualTo(ScanMasterQrViewModel.UiState.Error(ScanMasterQrViewModel.ErrorReason.OtherVersion))
    }

    @Test
    fun `an expired pairing is refused`() {
        sut.onScanned(ClientSetupCode.encode(withPairing.copy(pairing = pairing.copy(expiresAt = now - 1))))

        assertThat(sut.state.value).isEqualTo(ScanMasterQrViewModel.UiState.Error(ScanMasterQrViewModel.ErrorReason.Expired))
    }

    /** Like the PIN: a second pairing would quietly replace the first. */
    @Test
    fun `a code that pairs is refused on a paired phone`() {
        whenever(pairingRepository.isPaired()).thenReturn(true)

        sut.onScanned(ClientSetupCode.encode(withPairing))

        assertThat(sut.state.value).isEqualTo(ScanMasterQrViewModel.UiState.Error(ScanMasterQrViewModel.ErrorReason.AlreadyPaired))
    }

    @Test
    fun `a Nightscout-only code is fine on a paired phone`() {
        whenever(pairingRepository.isPaired()).thenReturn(true)

        sut.onScanned(ClientSetupCode.encode(nsOnly))

        assertThat(sut.state.value).isEqualTo(ScanMasterQrViewModel.UiState.Confirming(nsOnly))
    }

    /** The camera reports the same code many times: only the first one counts. */
    @Test
    fun `codes seen after the first are ignored`() {
        sut.onScanned(ClientSetupCode.encode(nsOnly))
        sut.onScanned("https://example.com")

        assertThat(sut.state.value).isEqualTo(ScanMasterQrViewModel.UiState.Confirming(nsOnly))
    }

    @Test
    fun `a Nightscout-only code sets up Nightscout and does not pair`() = runTest {
        sut.onScanned(ClientSetupCode.encode(nsOnly))

        sut.confirm()

        verify(preferences).put(StringKey.NsClientUrl, "https://ns.example")
        verify(preferences).put(StringKey.NsClientAccessToken, "aapstablet-0123456789abcdef")
        verify(preferences).put(BooleanKey.NsClient3UseWs, true)
        verify(pairingRepository, never()).pair(any(), any())
        assertThat(sut.state.value).isEqualTo(ScanMasterQrViewModel.UiState.Done(paired = false))
    }

    /**
     * The pairing is stored before the Nightscout settings change: the connection that the change starts is the one
     * that sends the hello, so the pairing must be there by then.
     */
    @Test
    fun `a code that pairs stores the pairing before Nightscout changes`() = runTest {
        whenever(pairingRepository.pair(pairing, now)).thenReturn(true)
        sut.onScanned(ClientSetupCode.encode(withPairing))

        sut.confirm()

        val order = inOrder(pairingRepository, preferences)
        order.verify(pairingRepository).pair(pairing, now)
        order.verify(preferences).put(StringKey.NsClientUrl, "https://ns.example")
        order.verify(preferences).put(StringKey.NsClientAccessToken, "aapstablet-0123456789abcdef")
        assertThat(sut.state.value).isEqualTo(ScanMasterQrViewModel.UiState.Done(paired = true))
    }

    /** A pairing that cannot keep its secret is not a pairing: nothing is changed. */
    @Test
    fun `when the secret cannot be stored nothing changes`() = runTest {
        whenever(pairingRepository.pair(pairing, now)).thenReturn(false)
        sut.onScanned(ClientSetupCode.encode(withPairing))

        sut.confirm()

        verify(preferences, never()).put(eq(StringKey.NsClientUrl), any<String>())
        assertThat(sut.state.value).isEqualTo(ScanMasterQrViewModel.UiState.Error(ScanMasterQrViewModel.ErrorReason.SecretStoreFailed))
    }

    @Test
    fun `NSClient is switched on when it is off`() = runTest {
        whenever(nsClientV3Plugin.isEnabled()).thenReturn(false)
        sut.onScanned(ClientSetupCode.encode(nsOnly))

        sut.confirm()

        verify(configBuilder).performPluginSwitch(nsClientV3Plugin, true, PluginType.SYNC)
    }
}
