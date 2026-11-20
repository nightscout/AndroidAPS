package app.aaps.pump.omnipod.omnipod5.ui

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.security.SecureO5RegistrationStorage
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.state.InMemoryO5PodStateManager
import app.aaps.pump.omnipod.omnipod5.keys.O5StringNonPreferenceKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Base64

/**
 * [O5CredentialImportViewModel] - manipulates the process-wide [O5RegistrationData] registry,
 * so every test cleans it up in @BeforeEach/@AfterEach to avoid cross-test pollution (same
 * discipline as [app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5CertificateStoreTest] etc).
 */
class O5CredentialImportViewModelTest {

    private val secureO5RegistrationStorage = mock<SecureO5RegistrationStorage>()
    private val podStateManager = InMemoryO5PodStateManager()
    private val preferences = mock<Preferences>()
    private val rh = mock<ResourceHelper>()
    private val podStateUpdates = MutableStateFlow("")

    private fun newViewModel() = O5CredentialImportViewModel(secureO5RegistrationStorage, podStateManager, preferences, rh)

    private fun packedCredential(controllerId: Long): String {
        val privB64 = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))
        val pubB64 = Base64.getEncoder().encodeToString(byteArrayOf(5, 6, 7, 8))
        return "$controllerId|$privB64|$pubB64||"
    }

    @BeforeEach
    fun clearRegistrationData() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        O5RegistrationData.allValues.forEach { O5RegistrationData.remove(it.controllerId) }
        podStateManager.reset()
        podStateUpdates.value = ""
        whenever(preferences.observe(O5StringNonPreferenceKey.PodState)).thenReturn(podStateUpdates)
        whenever(rh.gs(R.string.omnipod_5_certificate_import_paste_first)).thenReturn("Paste a certificate string first")
        whenever(rh.gs(R.string.omnipod_5_certificate_import_empty)).thenReturn("Empty certificate received")
        whenever(rh.gs(R.string.omnipod_5_certificate_import_parse_error)).thenReturn("Could not parse that certificate")
        whenever(rh.gs(R.string.omnipod_5_certificate_store_remove_blocked)).thenReturn("Certificate can only be removed when there is no active pod")
    }

    @AfterEach
    fun tearDown() {
        O5RegistrationData.allValues.forEach { O5RegistrationData.remove(it.controllerId) }
        podStateManager.reset()
        Dispatchers.resetMain()
    }

    @Test
    fun `importCurrentInput fails with a helpful message when the input is blank`() {
        val vm = newViewModel()
        vm.onInputChanged("   ")

        vm.importCurrentInput()

        assertThat(vm.importResult.value).isInstanceOf(ImportResult.Failure::class.java)
    }

    @Test
    fun `importCurrentInput fails when the certificate string is malformed`() {
        val vm = newViewModel()
        vm.onInputChanged("not a valid certificate string")

        vm.importCurrentInput()

        assertThat(vm.importResult.value).isInstanceOf(ImportResult.Failure::class.java)
    }

    @Test
    fun `importCurrentInput fails when certificate JSON misses a required key`() {
        val vm = newViewModel()
        vm.onInputChanged("""{"controllerId":"1","privateKey":"a","publicKey":"b","intermediateCA":"c"}""")

        vm.importCurrentInput()

        assertThat(vm.importResult.value).isEqualTo(ImportResult.Failure("Could not parse that certificate"))
    }

    @Test
    fun `importCurrentInput fails when certificate JSON has a null required key`() {
        val vm = newViewModel()
        vm.onInputChanged("""{"controllerId":"1","privateKey":null,"publicKey":"b","intermediateCA":"c","tlsCertificate":"d"}""")

        vm.importCurrentInput()

        assertThat(vm.importResult.value).isEqualTo(ImportResult.Failure("Could not parse that certificate"))
    }

    @Test
    fun `importCurrentInput installs, persists, and reports success for a well-formed certificate`() {
        val vm = newViewModel()
        val controllerId = 99887766L
        vm.onInputChanged(packedCredential(controllerId))

        vm.importCurrentInput()

        assertThat(vm.importResult.value).isEqualTo(ImportResult.Success(controllerId))
        assertThat(vm.inputText.value).isEmpty()
        assertThat(O5RegistrationData.contains(controllerId)).isTrue()
        verify(secureO5RegistrationStorage).persistEntry(
            O5RegistrationData.get(controllerId)!!,
            O5RegistrationData.O5RegistrationSource.IMPORTED
        )
    }

    @Test
    fun `importCurrentInput updates installedCredentials after a successful import`() {
        val vm = newViewModel()
        val controllerId = 55443322L
        vm.onInputChanged(packedCredential(controllerId))

        vm.importCurrentInput()

        assertThat(vm.installedCredentials.value).hasSize(1)
        assertThat(vm.installedCredentials.value[0].controllerId).isEqualTo(controllerId)
        assertThat(vm.installedCredentials.value[0].source).isEqualTo(O5RegistrationData.O5RegistrationSource.IMPORTED)
    }

    @Test
    fun `onInputChanged updates inputText and clears a stale import result`() {
        val vm = newViewModel()
        vm.onInputChanged("not valid")
        vm.importCurrentInput()
        assertThat(vm.importResult.value).isNotEqualTo(ImportResult.None)

        vm.onInputChanged("something new")

        assertThat(vm.inputText.value).isEqualTo("something new")
        assertThat(vm.importResult.value).isEqualTo(ImportResult.None)
    }

    @Test
    fun `removeCredential removes from the registry and persisted storage, then refreshes the list`() {
        val vm = newViewModel()
        val controllerId = 11223344L
        vm.onInputChanged(packedCredential(controllerId))
        vm.importCurrentInput()
        assertThat(vm.installedCredentials.value).hasSize(1)

        vm.removeCredential(controllerId)

        assertThat(O5RegistrationData.contains(controllerId)).isFalse()
        assertThat(vm.installedCredentials.value).isEmpty()
        verify(secureO5RegistrationStorage).removeEntry(controllerId)
    }

    @Test
    fun `removeCredential does not remove when there is an active pod`() {
        val vm = newViewModel()
        val controllerId = 11223344L
        vm.onInputChanged(packedCredential(controllerId))
        vm.importCurrentInput()
        podStateManager.activationProgress = ActivationProgress.COMPLETED
        podStateUpdates.value = "active"

        vm.removeCredential(controllerId)

        assertThat(O5RegistrationData.contains(controllerId)).isTrue()
        assertThat(vm.installedCredentials.value).hasSize(1)
        assertThat(vm.importResult.value).isEqualTo(ImportResult.RemoveBlocked)
        verify(secureO5RegistrationStorage, never()).removeEntry(controllerId)
    }
}
