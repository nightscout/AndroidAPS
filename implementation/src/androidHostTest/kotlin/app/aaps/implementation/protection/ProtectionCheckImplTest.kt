package app.aaps.implementation.protection

import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class ProtectionCheckImplTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var sut: ProtectionCheckImpl

    @BeforeEach
    fun setup() {
        sut = ProtectionCheckImpl(preferences, dateUtil)
    }

    /** Configure SETTINGS protection as prerequisite for BOLUS tests (hierarchy enforcement). */
    private fun enableSettingsProtection() {
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(2) // MASTER_PASSWORD
    }

    // --- isLocked ---

    @Test
    fun `isLocked returns false when no master password set`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("")
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
        assertThat(sut.isLocked(ProtectionCheck.Protection.PREFERENCES)).isFalse()
        assertThat(sut.isLocked(ProtectionCheck.Protection.APPLICATION)).isFalse()
    }

    @Test
    fun `isLocked returns false when protection type is NONE`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        enableSettingsProtection()
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(0) // NONE
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
    }

    @Test
    fun `isLocked returns true when master password protection enabled`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        enableSettingsProtection()
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0) // always ask
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isTrue()
    }

    @Test
    fun `isLocked returns true when biometric protection enabled`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(1) // BIOMETRIC
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        assertThat(sut.isLocked(ProtectionCheck.Protection.PREFERENCES)).isTrue()
    }

    @Test
    fun `isLocked returns false for custom password when password is empty`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        enableSettingsProtection()
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(3) // CUSTOM_PASSWORD
        `when`(preferences.get(StringKey.ProtectionBolusPassword)).thenReturn("")
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
    }

    @Test
    fun `isLocked returns true for custom password when password is set`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        enableSettingsProtection()
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(3) // CUSTOM_PASSWORD
        `when`(preferences.get(StringKey.ProtectionBolusPassword)).thenReturn("mypass")
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isTrue()
    }

    @Test
    fun `isLocked BOLUS returns false when SETTINGS is NONE despite BOLUS being set`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(0) // NONE — hierarchy enforcement
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
    }

    // --- Hierarchical session ---

    @Test
    fun `auth at PREFERENCES unlocks BOLUS and APPLICATION`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTypeApplication)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        sut.requestAuthorization(ProtectionCheck.Protection.PREFERENCES) {}
        val request = sut.pendingAuthRequest.value!!
        sut.completeAuthRequest(request.id, AuthorizationResult(ProtectionCheck.Protection.PREFERENCES, ProtectionResult.GRANTED))

        `when`(dateUtil.now()).thenReturn(1000_000L + 30_000L)

        assertThat(sut.isLocked(ProtectionCheck.Protection.PREFERENCES)).isFalse()
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
        assertThat(sut.isLocked(ProtectionCheck.Protection.APPLICATION)).isFalse()
    }

    @Test
    fun `auth at BOLUS does NOT unlock PREFERENCES`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) {}
        val request = sut.pendingAuthRequest.value!!
        sut.completeAuthRequest(request.id, AuthorizationResult(ProtectionCheck.Protection.BOLUS, ProtectionResult.GRANTED))

        `when`(dateUtil.now()).thenReturn(1000_000L + 30_000L)

        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
        assertThat(sut.isLocked(ProtectionCheck.Protection.PREFERENCES)).isTrue()
    }

    @Test
    fun `master password grants MASTER level and unlocks everything`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTypeApplication)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) {}
        val request = sut.pendingAuthRequest.value!!
        sut.completeAuthRequest(request.id, AuthorizationResult(ProtectionCheck.Protection.MASTER, ProtectionResult.GRANTED))

        `when`(dateUtil.now()).thenReturn(1000_000L + 30_000L)

        assertThat(sut.isLocked(ProtectionCheck.Protection.APPLICATION)).isFalse()
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
        assertThat(sut.isLocked(ProtectionCheck.Protection.PREFERENCES)).isFalse()
    }

    // --- Session timeout ---

    @Test
    fun `isLocked returns true after session expires`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        enableSettingsProtection()
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) {}
        val request = sut.pendingAuthRequest.value!!
        sut.completeAuthRequest(request.id, AuthorizationResult(ProtectionCheck.Protection.BOLUS, ProtectionResult.GRANTED))

        `when`(dateUtil.now()).thenReturn(1000_000L + 61_000L)
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isTrue()
    }

    @Test
    fun `zero timeout means always ask`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        enableSettingsProtection()
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0) // always ask
        `when`(dateUtil.now()).thenReturn(1000_000L)

        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) {}
        val request = sut.pendingAuthRequest.value!!
        sut.completeAuthRequest(request.id, AuthorizationResult(ProtectionCheck.Protection.BOLUS, ProtectionResult.GRANTED))

        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isTrue()
    }

    // --- resetAuthorization ---

    @Test
    fun `resetAuthorization clears session`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        enableSettingsProtection()
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) {}
        val request = sut.pendingAuthRequest.value!!
        sut.completeAuthRequest(request.id, AuthorizationResult(ProtectionCheck.Protection.BOLUS, ProtectionResult.GRANTED))

        `when`(dateUtil.now()).thenReturn(1000_000L + 30_000L)
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()

        sut.resetAuthorization()
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isTrue()
    }

    // --- requestAuthorization ---

    @Test
    fun `requestAuthorization grants MASTER immediately when no master password`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("")
        var result: AuthorizationResult? = null
        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) { result = it }
        assertThat(result?.outcome).isEqualTo(ProtectionResult.GRANTED)
        assertThat(result?.grantedLevel).isEqualTo(ProtectionCheck.Protection.MASTER)
        assertThat(sut.pendingAuthRequest.value).isNull()
    }

    @Test
    fun `requestAuthorization grants immediately when no protection configured at minimum level`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(0) // NONE
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(0) // NONE
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        var result: AuthorizationResult? = null
        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) { result = it }
        assertThat(result?.outcome).isEqualTo(ProtectionResult.GRANTED)
        assertThat(sut.pendingAuthRequest.value).isNull()
    }

    @Test
    fun `requestAuthorization auto-grants PREFERENCES when BOLUS and SETTINGS are both NONE`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(0) // NONE
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(0) // NONE
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        var result: AuthorizationResult? = null
        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) { result = it }
        assertThat(result?.grantedLevel).isEqualTo(ProtectionCheck.Protection.PREFERENCES)
    }

    @Test
    fun `requestAuthorization emits pending request when protection configured`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        var result: AuthorizationResult? = null
        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) { result = it }

        assertThat(result).isNull()
        assertThat(sut.pendingAuthRequest.value).isNotNull()
    }

    @Test
    fun `completeAuthRequest resolves pending request`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        var result: AuthorizationResult? = null
        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) { result = it }

        val requestId = sut.pendingAuthRequest.value!!.id
        sut.completeAuthRequest(requestId, AuthorizationResult(ProtectionCheck.Protection.BOLUS, ProtectionResult.GRANTED))

        assertThat(result?.outcome).isEqualTo(ProtectionResult.GRANTED)
        assertThat(sut.pendingAuthRequest.value).isNull()
    }

    @Test
    fun `denied request does not start session`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) {}
        val request = sut.pendingAuthRequest.value!!
        sut.completeAuthRequest(request.id, AuthorizationResult(null, ProtectionResult.DENIED))

        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isTrue()
    }

    @Test
    fun `session upgrade - auth at higher level upgrades existing session`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        sut.requestAuthorization(ProtectionCheck.Protection.BOLUS) {}
        val req1 = sut.pendingAuthRequest.value!!
        sut.completeAuthRequest(req1.id, AuthorizationResult(ProtectionCheck.Protection.BOLUS, ProtectionResult.GRANTED))

        `when`(dateUtil.now()).thenReturn(1000_000L + 10_000L)
        assertThat(sut.isLocked(ProtectionCheck.Protection.PREFERENCES)).isTrue()

        sut.requestAuthorization(ProtectionCheck.Protection.PREFERENCES) {}
        val req2 = sut.pendingAuthRequest.value!!
        sut.completeAuthRequest(req2.id, AuthorizationResult(ProtectionCheck.Protection.PREFERENCES, ProtectionResult.GRANTED))

        `when`(dateUtil.now()).thenReturn(1000_000L + 20_000L)
        assertThat(sut.isLocked(ProtectionCheck.Protection.PREFERENCES)).isFalse()
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
    }

    // --- Legacy requestProtection delegation ---

    @Test
    fun `requestProtection grants immediately when no master password`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("")
        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) { result = it }
        assertThat(result).isEqualTo(ProtectionResult.GRANTED)
    }

    @Test
    fun `requestProtection grants for NONE protection`() {
        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.NONE) { result = it }
        assertThat(result).isEqualTo(ProtectionResult.GRANTED)
    }

    // --- A custom password or PIN that was never entered is no protection (#5462) ---

    private fun assertGrantedWithoutPrompt(protection: ProtectionCheck.Protection) {
        var result: ProtectionResult? = null
        sut.requestProtection(protection) { result = it }
        assertThat(result).isEqualTo(ProtectionResult.GRANTED)
        assertThat(sut.pendingAuthRequest.value).isNull()
    }

    private fun stubMasterPasswordAlwaysAsk() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        `when`(dateUtil.now()).thenReturn(1000_000L)
    }

    @Test
    fun `requestProtection grants APPLICATION when custom password was never entered`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeApplication)).thenReturn(3) // CUSTOM_PASSWORD
        `when`(preferences.get(StringKey.ProtectionApplicationPassword)).thenReturn("")
        assertGrantedWithoutPrompt(ProtectionCheck.Protection.APPLICATION)
    }

    @Test
    fun `requestProtection grants APPLICATION when custom PIN was never entered`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeApplication)).thenReturn(4) // CUSTOM_PIN
        `when`(preferences.get(StringKey.ProtectionApplicationPin)).thenReturn("")
        assertGrantedWithoutPrompt(ProtectionCheck.Protection.APPLICATION)
    }

    @Test
    fun `requestProtection grants BOLUS when custom password was never entered`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(3) // CUSTOM_PASSWORD
        `when`(preferences.get(StringKey.ProtectionBolusPassword)).thenReturn("")
        assertGrantedWithoutPrompt(ProtectionCheck.Protection.BOLUS)
    }

    @Test
    fun `requestProtection grants BOLUS when custom PIN was never entered`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(4) // CUSTOM_PIN
        `when`(preferences.get(StringKey.ProtectionBolusPin)).thenReturn("")
        assertGrantedWithoutPrompt(ProtectionCheck.Protection.BOLUS)
    }

    @Test
    fun `requestProtection grants PREFERENCES when custom password was never entered`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(3) // CUSTOM_PASSWORD
        `when`(preferences.get(StringKey.ProtectionSettingsPassword)).thenReturn("")
        assertGrantedWithoutPrompt(ProtectionCheck.Protection.PREFERENCES)
    }

    @Test
    fun `requestProtection grants PREFERENCES when custom PIN was never entered`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(4) // CUSTOM_PIN
        `when`(preferences.get(StringKey.ProtectionSettingsPin)).thenReturn("")
        assertGrantedWithoutPrompt(ProtectionCheck.Protection.PREFERENCES)
    }

    @Test
    fun `requestProtection still asks when custom password is set`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeApplication)).thenReturn(3) // CUSTOM_PASSWORD
        `when`(preferences.get(StringKey.ProtectionApplicationPassword)).thenReturn("hash")

        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.APPLICATION) { result = it }

        assertThat(result).isNull()
        assertThat(sut.pendingAuthRequest.value).isNotNull()
    }

    @Test
    fun `requestProtection still asks when custom PIN is set`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(4) // CUSTOM_PIN
        `when`(preferences.get(StringKey.ProtectionBolusPin)).thenReturn("hash")

        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) { result = it }

        assertThat(result).isNull()
        assertThat(sut.pendingAuthRequest.value).isNotNull()
    }

    @Test
    fun `requestProtection still asks for master password type`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeApplication)).thenReturn(2) // MASTER_PASSWORD

        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.APPLICATION) { result = it }

        assertThat(result).isNull()
        assertThat(sut.pendingAuthRequest.value).isNotNull()
    }

    // --- The master password always works, so a forgotten password or PIN is no lockout ---

    @Test
    fun `requestProtection offers the master password next to a custom PIN`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeApplication)).thenReturn(4) // CUSTOM_PIN
        `when`(preferences.get(StringKey.ProtectionApplicationPin)).thenReturn("pinHash")

        sut.requestProtection(ProtectionCheck.Protection.APPLICATION) {}

        val hashes = sut.pendingAuthRequest.value!!.availableMethods.map { it.credentialHash }
        assertThat(hashes).containsExactly("pinHash", "secret")
    }

    @Test
    fun `requestProtection offers the master password next to a custom password`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeApplication)).thenReturn(3) // CUSTOM_PASSWORD
        `when`(preferences.get(StringKey.ProtectionApplicationPassword)).thenReturn("passwordHash")

        sut.requestProtection(ProtectionCheck.Protection.APPLICATION) {}

        val hashes = sut.pendingAuthRequest.value!!.availableMethods.map { it.credentialHash }
        assertThat(hashes).containsExactly("passwordHash", "secret")
    }

    @Test
    fun `requestProtection answers with the outcome of the prompt`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeApplication)).thenReturn(2) // MASTER_PASSWORD

        for ((grantedLevel, outcome) in listOf(
            ProtectionCheck.Protection.MASTER to ProtectionResult.GRANTED,
            null to ProtectionResult.DENIED,
            null to ProtectionResult.CANCELLED
        )) {
            var result: ProtectionResult? = null
            sut.requestProtection(ProtectionCheck.Protection.APPLICATION) { result = it }
            sut.completeAuthRequest(sut.pendingAuthRequest.value!!.id, AuthorizationResult(grantedLevel, outcome))

            assertThat(result).isEqualTo(outcome)
            assertThat(sut.pendingAuthRequest.value).isNull()
        }
    }

    /** The only behaviour `requestProtection` keeps apart from `requestAuthorization`. */
    @Test
    fun `requestProtection asks for a bolus lock with its own PIN when settings level is off`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(0) // NONE
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(4) // CUSTOM_PIN
        `when`(preferences.get(StringKey.ProtectionBolusPin)).thenReturn("pinHash")

        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) { result = it }

        assertThat(result).isNull()
        val hashes = sut.pendingAuthRequest.value!!.availableMethods.map { it.credentialHash }
        assertThat(hashes).containsExactly("pinHash", "secret")
    }

    @Test
    fun `requestProtection offers biometric when the level uses it`() {
        stubMasterPasswordAlwaysAsk()
        `when`(preferences.get(IntKey.ProtectionTypeApplication)).thenReturn(1) // BIOMETRIC

        sut.requestProtection(ProtectionCheck.Protection.APPLICATION) {}

        val request = sut.pendingAuthRequest.value!!
        assertThat(request.hasBiometric).isTrue()
        assertThat(request.biometricGrantsLevel).isEqualTo(ProtectionCheck.Protection.APPLICATION)
        assertThat(request.availableMethods.map { it.credentialHash }).contains("secret")
    }

    @Test
    fun `requestProtection for MASTER asks for the master password`() {
        stubMasterPasswordAlwaysAsk()

        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.MASTER) { result = it }

        assertThat(result).isNull()
        assertThat(sut.pendingAuthRequest.value!!.availableMethods.map { it.credentialHash }).containsExactly("secret")
    }
}
