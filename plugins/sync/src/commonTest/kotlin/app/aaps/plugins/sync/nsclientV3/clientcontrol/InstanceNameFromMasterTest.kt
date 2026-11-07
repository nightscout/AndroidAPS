package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.keys.StringKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The name a client was added with on the master, as its instance name. It must pass the instance name check
 * (`StringValidator.personName`: ASCII letters, digits, spaces, `'` and `-`), or the client could not keep it.
 */
class InstanceNameFromMasterTest {

    @Test
    fun `letters with marks lose them`() {
        assertEquals("Milos", asciiInstanceName("Miloš"))
        assertEquals("Jiri Novak", asciiInstanceName("Jiří Novák"))
        assertEquals("Lukasz Zolw", asciiInstanceName("Łukasz Żółw"))
        assertEquals("Francois", asciiInstanceName("François"))
    }

    @Test
    fun `letters written as two in ASCII`() {
        assertEquals("Strasse", asciiInstanceName("Straße"))
        assertEquals("AEro", asciiInstanceName("Ærø"))
    }

    @Test
    fun `a device name keeps its digits`() {
        assertEquals("S10e", asciiInstanceName("S10e"))
    }

    @Test
    fun `apostrophes and hyphens stay`() {
        assertEquals("Mom's phone", asciiInstanceName("Mom's phone"))
        assertEquals("Anna-Lena", asciiInstanceName("Anna-Lena"))
    }

    @Test
    fun `other characters become one space`() {
        assertEquals("phone 2", asciiInstanceName("phone_2"))
        assertEquals("Kid tablet", asciiInstanceName("  Kid   (tablet) "))
    }

    @Test
    fun `a name in another script leaves nothing`() {
        assertEquals("", asciiInstanceName("张伟"))
    }

    /** What comes out is always a name the instance name check takes. */
    @Test
    fun `every result passes the instance name check`() {
        val check = StringKey.GeneralInstanceName.validator
        listOf("Miloš", "Jiří Novák", "Straße", "Ærø", "S10e", "Mom's phone", "phone_2", "  Kid   (tablet) ", "Zoë #1").forEach {
            assertTrue(check.validate(asciiInstanceName(it)).isValid, "$it -> ${asciiInstanceName(it)}")
        }
    }
}
