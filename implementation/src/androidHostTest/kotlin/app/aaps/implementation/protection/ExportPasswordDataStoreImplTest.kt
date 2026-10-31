package app.aaps.implementation.protection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.ExportPasswordPlatform
import app.aaps.core.interfaces.protection.PasswordHasher
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The rules this class owns, which could not be tested before it moved to commonMain.
 *
 * The old test could only reach the disabled path: everything else needed a real `Context`, a real
 * DataStore and a real keystore, and it said so in two commented out cases. Storage is now
 * [ExportPasswordPlatform], so a fake covers it and the expiry window, the grace period, the master
 * password cross check and the legacy alias handling are all reachable.
 *
 * These live in `androidHostTest` rather than `commonTest` only because `Preferences` has seventy
 * methods and Mockito is not available in common code. The class under test is pure Kotlin, so the
 * JVM is a fair place to check its behaviour.
 */
class ExportPasswordDataStoreImplTest {

    private val aapsLogger: AAPSLogger = mock()
    private val preferences: Preferences = mock()
    private val dateUtil: DateUtil = mock()
    private val secureEncrypt: SecureEncrypt = mock()
    private val passwordHasher: PasswordHasher = mock()
    private val platform = FakePlatform()

    private val sut by lazy {
        ExportPasswordDataStoreImpl(aapsLogger, preferences, platform, dateUtil, secureEncrypt, passwordHasher)
    }

    /** A secret that carries the current alias, so it is not treated as a legacy one. */
    private val currentSecret = "hash:${ExportPasswordDataStoreImpl.KEYSTORE_ALIAS}:iv:cipher"
    private val now = 1_000_000_000_000L
    private val fiveWeeks = 35 * 24 * 3600 * 1000L

    /** In memory storage. The real ones write to DataStore or to a file; the rules do not care. */
    private class FakePlatform : ExportPasswordPlatform {

        var stored: ExportPasswordPlatform.Stored? = null
        var validity: ExportPasswordPlatform.Validity? = null
        var clearCount = 0

        override fun read(): ExportPasswordPlatform.Stored? = stored
        override fun write(secret: String, timestamp: Long) {
            stored = ExportPasswordPlatform.Stored(secret, timestamp)
        }

        override fun clear() {
            stored = null
            clearCount++
        }

        override fun shortenedValidity(): ExportPasswordPlatform.Validity? = validity
    }

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenReturn(now)
    }

    private fun enable() {
        whenever(preferences.get(BooleanKey.MaintenanceEnableExportSettingsAutomation)).thenReturn(true)
    }

    private fun masterPasswordMatches(matches: Boolean) {
        whenever(preferences.getIfExists(StringKey.ProtectionMasterPassword)).thenReturn("masterHash")
        whenever(secureEncrypt.decrypt(any())).thenReturn("plain")
        whenever(passwordHasher.checkPassword("plain", "masterHash")).thenReturn(matches)
    }

    @Test
    fun `does nothing at all while the user has it switched off`() {
        whenever(preferences.get(BooleanKey.MaintenanceEnableExportSettingsAutomation)).thenReturn(false)

        assertFalse(sut.exportPasswordStoreEnabled())
        assertTrue(sut.clearPasswordDataStore().isEmpty())
        // The password comes straight back, so the caller can still use it for this one export.
        assertEquals("secret", sut.putPasswordToDataStore("secret"))
        assertEquals(Triple("", true, true), sut.getPasswordFromDataStore())
        assertNull(platform.stored)
    }

    @Test
    fun `stores the encrypted secret and not the password`() {
        enable()
        whenever(secureEncrypt.encrypt("plain", ExportPasswordDataStoreImpl.KEYSTORE_ALIAS)).thenReturn(currentSecret)

        assertEquals(currentSecret, sut.putPasswordToDataStore("plain"))
        assertEquals(currentSecret, platform.stored?.secret)
        assertEquals(now, platform.stored?.timestamp)
    }

    @Test
    fun `returns a stored password that is still valid`() {
        enable()
        masterPasswordMatches(true)
        platform.stored = ExportPasswordPlatform.Stored(currentSecret, now)

        assertEquals(Triple(currentSecret, false, false), sut.getPasswordFromDataStore())
    }

    @Test
    fun `warns that a password is about to expire inside the grace period`() {
        enable()
        masterPasswordMatches(true)
        // Written 30 days ago: past the point where the last week starts, but not yet expired.
        platform.stored = ExportPasswordPlatform.Stored(currentSecret, now - 30 * 24 * 3600 * 1000L)

        val (password, expired, aboutToExpire) = sut.getPasswordFromDataStore()
        assertEquals(currentSecret, password)
        assertFalse(expired)
        assertTrue(aboutToExpire)
    }

    @Test
    fun `drops a password once the validity window has passed`() {
        enable()
        platform.stored = ExportPasswordPlatform.Stored(currentSecret, now - fiveWeeks - 1)

        assertEquals(Triple("", true, true), sut.getPasswordFromDataStore())
        assertNull(platform.stored)
    }

    @Test
    fun `drops a password that no longer matches the master password`() {
        // The point of the whole cross check: the master was changed, so an unattended export must
        // not keep running with the secret taken from the old one.
        enable()
        masterPasswordMatches(false)
        platform.stored = ExportPasswordPlatform.Stored(currentSecret, now)

        assertEquals(Triple("", true, true), sut.getPasswordFromDataStore())
        assertNull(platform.stored)
    }

    @Test
    fun `drops a password when there is no master password at all`() {
        enable()
        whenever(preferences.getIfExists(StringKey.ProtectionMasterPassword)).thenReturn(null)
        platform.stored = ExportPasswordPlatform.Stored(currentSecret, now)

        assertEquals(Triple("", true, true), sut.getPasswordFromDataStore())
        assertNull(platform.stored)
    }

    @Test
    fun `drops a secret written under an older alias and deletes its key`() {
        enable()
        platform.stored = ExportPasswordPlatform.Stored("hash:UnattendedExportAlias:iv:cipher", now)

        assertEquals(Triple("", true, true), sut.getPasswordFromDataStore())
        assertNull(platform.stored)
        verify(secureEncrypt).deleteKey("UnattendedExportAlias")
        // The old blob is never decrypted: the whole point is that its key is the wrong one.
        verify(secureEncrypt, never()).decrypt(any())
    }

    @Test
    fun `uses a shortened window when the platform offers one`() {
        enable()
        masterPasswordMatches(true)
        platform.validity = ExportPasswordPlatform.Validity(window = 20 * 60 * 1000L, gracePeriod = 10 * 60 * 1000L)
        // Half an hour old. Still inside the normal five weeks, so this only expires if the
        // shortened window is the one being applied.
        platform.stored = ExportPasswordPlatform.Stored(currentSecret, now - 30 * 60 * 1000L)

        assertEquals(Triple("", true, true), sut.getPasswordFromDataStore())
    }

    @Test
    fun `an empty store reports expired without clearing anything`() {
        enable()

        assertEquals(Triple("", true, true), sut.getPasswordFromDataStore())
        assertEquals(0, platform.clearCount)
    }
}
