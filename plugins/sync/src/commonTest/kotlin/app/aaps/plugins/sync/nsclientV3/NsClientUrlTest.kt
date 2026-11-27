package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.keys.StringKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A space in the Nightscout address crashed the app: Ktor throws on a host with a space in it (Crashlytics, 2026-11,
 * "Host cannot contain whitespace characters"). A phone keyboard easily adds one, often at the end.
 */
class NsClientUrlTest {

    @Test
    fun `the base url has no spaces`() {
        assertEquals("my.nightscout.com", nsClientBaseUrl("https://my.nightscout .com"))
        assertEquals("my.nightscout.com", nsClientBaseUrl("https://my.nightscout.com/ "))
        assertEquals("my.nightscout.com", nsClientBaseUrl(" https://My.Nightscout.com\t"))
    }

    @Test
    fun `an address without spaces stays as it was`() {
        assertEquals("my.nightscout.com", nsClientBaseUrl("https://my.nightscout.com/"))
        assertEquals("my.nightscout.com/ns", nsClientBaseUrl("https://my.nightscout.com/ns"))
        assertEquals("https://my.nightscout.com/", nsClientUrlWithoutSpaces("https://my.nightscout.com/"))
    }

    @Test
    fun `the setting does not take an address with a space`() {
        val validator = StringKey.NsClientUrl.validator
        assertFalse(validator.validate("https://my.nightscout .com").isValid)
        assertFalse(validator.validate("https://my.nightscout.com ").isValid)
        assertTrue(validator.validate("https://my.nightscout.com").isValid)
        assertTrue(validator.validate("").isValid)
    }

    @Test
    fun `the setting trims the address on save`() {
        val validator = StringKey.NsClientUrl.validator
        assertEquals("https://my.nightscout.com", validator.clean(" https://my.nightscout.com \n"))
        assertTrue(validator.validate(validator.clean("https://my.nightscout.com ")).isValid)
        // A space inside is not trimmed: the user sees the error and the space.
        assertEquals("https://my.nightscout .com", validator.clean("https://my.nightscout .com"))
        assertFalse(validator.validate(validator.clean("https://my.nightscout .com")).isValid)
    }
}
