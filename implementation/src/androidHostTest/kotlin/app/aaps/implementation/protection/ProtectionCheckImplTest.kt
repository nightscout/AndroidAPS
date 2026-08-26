package app.aaps.implementation.protection

import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.PasswordCheck
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
    @Mock lateinit var passwordCheck: PasswordCheck
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var sut: ProtectionCheckImpl

    @BeforeEach
    fun setup() {
        sut = ProtectionCheckImpl(preferences, passwordCheck, dateUtil)
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
}
