package app.aaps.plugins.sync.nsclientV3.clientcontrol.compose

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.clientcontrol.AuthorizedClient
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.plugins.sync.nsclientV3.clientcontrol.AuthorizedClientsRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.PairingOfferPublisher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit test for the SYNCHRONOUS dialog/switch logic of [AuthorizedClientsViewModel].
 *
 * The `init{}` collector (repository.observe().collect{}) is launched on viewModelScope, so a
 * StandardTestDispatcher as Main defers it (never advanced) and construction stays clean. The
 * `clients` / `clientControlEnabled` StateFlow field-initializers call the collaborator flows at
 * construction, so those are stubbed to non-null flows here. Tests then exercise the pure,
 * synchronous state-mutating methods against the default state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class AuthorizedClientsViewModelTest {

    @Mock private lateinit var repository: AuthorizedClientsRepository
    @Mock private lateinit var offerPublisher: PairingOfferPublisher
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var preferences: Preferences

    private lateinit var sut: AuthorizedClientsViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(StandardTestDispatcher())
        // Field-initializers build StateFlows over these collaborators at construction.
        whenever(repository.observe()).thenReturn(emptyFlow())
        whenever(preferences.observe(BooleanKey.NsClientAllowClientControl)).thenReturn(MutableStateFlow(false))
        whenever(preferences.observe(BooleanKey.GeneralSimpleMode)).thenReturn(MutableStateFlow(false))
        whenever(preferences.get(BooleanKey.NsClientAllowClientControl)).thenReturn(false)
        sut = AuthorizedClientsViewModel(repository, offerPublisher, dateUtil, rh, preferences)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `requestAdd opens the EnterName dialog`() {
        sut.requestAdd()

        assertThat(sut.dialogState.value).isEqualTo(AuthorizedClientsViewModel.DialogState.EnterName)
    }

    @Test
    fun `requestDelete opens ConfirmDelete for the given client`() {
        val client = AuthorizedClient(
            clientId = "client-1",
            name = "Phone",
            encryptedSecret = "enc",
            state = ClientState.Active,
            createdAt = 1_000L
        )

        sut.requestDelete(client)

        assertThat(sut.dialogState.value).isEqualTo(AuthorizedClientsViewModel.DialogState.ConfirmDelete(client))
    }

    @Test
    fun `dismissDialog clears the dialog state`() {
        sut.requestAdd()
        assertThat(sut.dialogState.value).isNotNull()

        sut.dismissDialog()

        assertThat(sut.dialogState.value).isNull()
    }

    @Test
    fun `setClientControlEnabled writes the allow-client-control preference`() {
        sut.setClientControlEnabled(true)

        verify(preferences).put(BooleanKey.NsClientAllowClientControl, true)
    }

    @Test
    fun `default dialog and pairing state are empty`() {
        assertThat(sut.dialogState.value).isNull()
        assertThat(sut.pairingOffer.value).isNull()
    }
}
