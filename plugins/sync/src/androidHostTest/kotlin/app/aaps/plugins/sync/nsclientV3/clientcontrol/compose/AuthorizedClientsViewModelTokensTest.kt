package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.authorization.NSAuthSubject
import app.aaps.core.nssdk.localmodel.clientcontrol.AuthorizedClient
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientSetupCode
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.plugins.sync.nsclientV3.clientcontrol.AuthorizedClientsRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.ClientTokens
import app.aaps.plugins.sync.nsclientV3.clientcontrol.MasterSigningKey
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PairingOfferPublisher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** The QR code ways of adding a client and the client tokens, with the coroutines run at once. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AuthorizedClientsViewModelTokensTest {

    @Mock private lateinit var repository: AuthorizedClientsRepository
    @Mock private lateinit var offerPublisher: PairingOfferPublisher
    @Mock private lateinit var clientTokens: ClientTokens
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var masterSigningKey: MasterSigningKey

    private val now = 1_000_000L
    private val tabletToken = NSAuthSubject(id = "s1", name = "aaps-Tablet", roles = listOf(ClientTokens.ROLE_NAME), accessToken = "aapstablet-0123456789abcdef")

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(repository.observe()).thenReturn(emptyFlow())
        whenever(preferences.observe(BooleanKey.HardenedMode)).thenReturn(MutableStateFlow(true))
        whenever(preferences.observe(BooleanKey.GeneralSimpleMode)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(StringNonKey.NsClientControlClientId)).thenReturn(MutableStateFlow(""))
        whenever(preferences.get(BooleanKey.HardenedMode)).thenReturn(true)
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("https://ns.example")
        whenever(preferences.get(StringNonKey.NsClientControlOwnInstallId)).thenReturn("master-1")
        whenever(dateUtil.now()).thenReturn(now)
        whenever(clientTokens.isClientToken(any())).thenAnswer { (it.arguments[0] as NSAuthSubject).roles == listOf(ClientTokens.ROLE_NAME) }
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun newSut(): AuthorizedClientsViewModel {
        whenever(clientTokens.ensureRole(false)).thenReturn(ClientTokens.Result.Ok(Unit))
        whenever(clientTokens.subjects()).thenReturn(ClientTokens.Result.Ok(listOf(tabletToken.copy(accessToken = null))))
        whenever(repository.current(now)).thenReturn(emptyList())
        whenever(masterSigningKey.publicKey()).thenReturn("master-key")
        return AuthorizedClientsViewModel(repository, offerPublisher, clientTokens, dateUtil, rh, preferences, masterSigningKey)
    }

    private fun shownCode(sut: AuthorizedClientsViewModel): ClientSetupCode {
        val shown = sut.qrDialog.value as AuthorizedClientsViewModel.QrDialog.Shown
        return (ClientSetupCode.decode(shown.text) as ClientSetupCode.Decoded.Ok).code
    }

    @Test
    fun `a Nightscout-only QR code carries the address and the client's own token, and adds no client`() = runTest {
        val sut = newSut()
        whenever(clientTokens.tokenFor("Tablet")).thenReturn(ClientTokens.Result.Ok(tabletToken))

        sut.confirmAdd(" Tablet ", null, AuthorizedClientsViewModel.AddMethod.QrNightscoutOnly)

        val code = shownCode(sut)
        assertThat(code.url).isEqualTo("https://ns.example")
        assertThat(code.token).isEqualTo("aapstablet-0123456789abcdef")
        assertThat(code.pairing).isNull()
        // A phone that only reads Nightscout has no pong: the code is the only way it gets the master's key.
        assertThat(code.signingKey).isEqualTo("master-key")
        assertThat(code.name).isEqualTo("Tablet")
        verify(repository, never()).addPending(any(), any(), any(), any(), anyOrNull())
    }

    /** The token is made first, and the new client knows it from the start, so removing the client removes it too. */
    @Test
    fun `a QR code that pairs adds the client with its token name and carries the pairing`() = runTest {
        val sut = newSut()
        whenever(clientTokens.tokenFor("Tablet")).thenReturn(ClientTokens.Result.Ok(tabletToken))
        val entry = AuthorizedClient(clientId = "client-1", name = "Tablet", encryptedSecret = "enc", state = ClientState.Pending, createdAt = now, pairExpiresAt = now + 120_000L, nsSubject = "aaps-Tablet")
        whenever(repository.addPending("Tablet", ClientRole.MealAssistant, AuthorizedClientsViewModel.PAIR_TTL_MS, now, "aaps-Tablet"))
            .thenReturn(AuthorizedClientsRepository.PendingResult(entry, "ab".repeat(32)))

        sut.confirmAdd("Tablet", ClientRole.MealAssistant, AuthorizedClientsViewModel.AddMethod.QrWithPairing)

        val code = shownCode(sut)
        assertThat(code.token).isEqualTo("aapstablet-0123456789abcdef")
        assertThat(code.pairing?.clientId).isEqualTo("client-1")
        assertThat(code.pairing?.masterInstallId).isEqualTo("master-1")
        assertThat(code.pairing?.expiresAt).isEqualTo(now + 120_000L)
        assertThat((sut.qrDialog.value as AuthorizedClientsViewModel.QrDialog.Shown).clientId).isEqualTo("client-1")
    }

    @Test
    fun `without admin rights there is no QR code and no client`() = runTest {
        val sut = newSut()
        whenever(clientTokens.tokenFor("Tablet")).thenReturn(ClientTokens.Result.NotAllowed)

        sut.confirmAdd("Tablet", ClientRole.Admin, AuthorizedClientsViewModel.AddMethod.QrWithPairing)

        assertThat(sut.qrDialog.value).isEqualTo(AuthorizedClientsViewModel.QrDialog.Problem(AuthorizedClientsViewModel.TokenProblem.NotAllowed))
        verify(repository, never()).addPending(any(), any(), any(), any(), anyOrNull())
    }

    /** Two clients of one name would share one token, and removing one would cut off the other. */
    @Test
    fun `a name that a client has already gets no token`() = runTest {
        val sut = newSut()
        whenever(repository.current(now)).thenReturn(listOf(AuthorizedClient(clientId = "c0", name = "tablet", encryptedSecret = "enc", state = ClientState.Active, createdAt = 0L)))

        sut.confirmAdd("Tablet", ClientRole.Admin, AuthorizedClientsViewModel.AddMethod.QrWithPairing)

        assertThat(sut.qrDialog.value).isEqualTo(AuthorizedClientsViewModel.QrDialog.Problem(AuthorizedClientsViewModel.TokenProblem.NameInUse))
        verify(clientTokens, never()).tokenFor(any())
    }

    @Test
    fun `a client token that no client writes with is listed as not paired`() = runTest {
        val sut = newSut()
        // The list is shared only while the screen collects it.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sut.unpairedTokens.collect {} }

        assertThat(sut.unpairedTokens.value.map { it.name }).containsExactly("aaps-Tablet")
    }

    @Test
    fun `removing a client deletes the client token it writes with`() = runTest {
        val sut = newSut()
        val client = AuthorizedClient(clientId = "c1", name = "Tablet", encryptedSecret = "enc", state = ClientState.Active, createdAt = 0L, nsSubject = "aaps-Tablet")
        whenever(clientTokens.deleteByName("aaps-Tablet")).thenReturn(ClientTokens.Result.Ok(1))

        sut.requestDelete(client)
        sut.confirmDelete()

        verify(repository).delete("c1")
        verify(clientTokens).deleteByName("aaps-Tablet")
    }

    @Test
    fun `deleting a token without a client deletes it by its id`() = runTest {
        val sut = newSut()
        whenever(clientTokens.delete("s1")).thenReturn(ClientTokens.Result.Ok(Unit))

        sut.requestDeleteToken(tabletToken)
        sut.confirmDelete()

        verify(clientTokens).delete(eq("s1"))
        verify(repository, never()).delete(any())
    }
}
