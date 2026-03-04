package app.aaps.implementation.protection

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
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(0) // NONE
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
    }

    @Test
    fun `isLocked returns true when master password protection enabled`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
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
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(3) // CUSTOM_PASSWORD
        `when`(preferences.get(StringKey.ProtectionBolusPassword)).thenReturn("")
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
    }

    @Test
    fun `isLocked returns true for custom password when password is set`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(3) // CUSTOM_PASSWORD
        `when`(preferences.get(StringKey.ProtectionBolusPassword)).thenReturn("mypass")
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isTrue()
    }

    // --- Session timeout ---

    @Test
    fun `isLocked returns false during active session`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        // Simulate successful auth by calling requestProtection and completing it
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) {}
        val request = sut.pendingRequest.value!!
        sut.completeRequest(request.id, ProtectionResult.GRANTED)

        // Within session — should be unlocked
        `when`(dateUtil.now()).thenReturn(1000_000L + 30_000L) // 30s later
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
    }

    @Test
    fun `isLocked returns true after session expires`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        // Simulate successful auth
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) {}
        val request = sut.pendingRequest.value!!
        sut.completeRequest(request.id, ProtectionResult.GRANTED)

        // Session expired — should be locked again
        `when`(dateUtil.now()).thenReturn(1000_000L + 61_000L) // 61s later
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isTrue()
    }

    @Test
    fun `zero timeout means always ask`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0) // always ask
        `when`(dateUtil.now()).thenReturn(1000_000L)

        // Simulate successful auth
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) {}
        val request = sut.pendingRequest.value!!
        sut.completeRequest(request.id, ProtectionResult.GRANTED)

        // Even immediately after — still locked (0 = always ask)
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isTrue()
    }

    // --- Per-level independence ---

    @Test
    fun `sessions are independent per protection level`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTypeSettings)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        // Auth BOLUS only
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) {}
        val bolusRequest = sut.pendingRequest.value!!
        sut.completeRequest(bolusRequest.id, ProtectionResult.GRANTED)

        `when`(dateUtil.now()).thenReturn(1000_000L + 30_000L)

        // BOLUS session active, PREFERENCES still locked
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()
        assertThat(sut.isLocked(ProtectionCheck.Protection.PREFERENCES)).isTrue()
    }

    // --- resetAuthorization ---

    @Test
    fun `resetAuthorization clears all sessions`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        // Auth BOLUS
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) {}
        val request = sut.pendingRequest.value!!
        sut.completeRequest(request.id, ProtectionResult.GRANTED)

        `when`(dateUtil.now()).thenReturn(1000_000L + 30_000L)
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isFalse()

        // Reset
        sut.resetAuthorization()
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isTrue()
    }

    // --- requestProtection ---

    @Test
    fun `requestProtection grants immediately when no master password`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("")
        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) { result = it }
        assertThat(result).isEqualTo(ProtectionResult.GRANTED)
        assertThat(sut.pendingRequest.value).isNull()
    }

    @Test
    fun `requestProtection grants immediately when protection type is NONE`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(0) // NONE
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) { result = it }
        assertThat(result).isEqualTo(ProtectionResult.GRANTED)
        assertThat(sut.pendingRequest.value).isNull()
    }

    @Test
    fun `requestProtection emits pending request when password protection enabled`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) { result = it }

        // Not resolved yet — pending
        assertThat(result).isNull()
        assertThat(sut.pendingRequest.value).isNotNull()
    }

    @Test
    fun `completeRequest resolves pending request`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) { result = it }

        val requestId = sut.pendingRequest.value!!.id
        sut.completeRequest(requestId, ProtectionResult.GRANTED)

        assertThat(result).isEqualTo(ProtectionResult.GRANTED)
        assertThat(sut.pendingRequest.value).isNull()
    }

    @Test
    fun `completeRequest with wrong id does nothing`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(0)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        var result: ProtectionResult? = null
        sut.requestProtection(ProtectionCheck.Protection.BOLUS) { result = it }

        sut.completeRequest(999L, ProtectionResult.GRANTED)

        assertThat(result).isNull()
        assertThat(sut.pendingRequest.value).isNotNull()
    }

    @Test
    fun `denied request does not start session`() {
        `when`(preferences.get(StringKey.ProtectionMasterPassword)).thenReturn("secret")
        `when`(preferences.get(IntKey.ProtectionTypeBolus)).thenReturn(2) // MASTER_PASSWORD
        `when`(preferences.get(IntKey.ProtectionTimeout)).thenReturn(60)
        `when`(dateUtil.now()).thenReturn(1000_000L)

        sut.requestProtection(ProtectionCheck.Protection.BOLUS) {}
        val request = sut.pendingRequest.value!!
        sut.completeRequest(request.id, ProtectionResult.DENIED)

        // Should still be locked — denied auth doesn't start a session
        assertThat(sut.isLocked(ProtectionCheck.Protection.BOLUS)).isTrue()
    }
}
