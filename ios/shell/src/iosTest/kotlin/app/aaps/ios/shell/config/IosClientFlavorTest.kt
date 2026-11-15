package app.aaps.ios.shell.config

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which client a bundle identifier names.
 *
 * One Kotlin framework is linked into both AAPSClient and AAPSClient2, so this is the only thing
 * telling the two apart at runtime. It used to be written down as "client 1" and was therefore wrong
 * in AAPSClient2 - where the overview tints itself by these flags, so both clients drew the same
 * colour and the tint stopped distinguishing anything.
 *
 * Tested as a pure function because the real config reads the running bundle, and under test that is
 * always the test binary - so `IosClientConfig` itself can never observe anything but client 1 here.
 */
class IosClientFlavorTest {

    @Test
    fun `the plain identifier is client one`() {
        assertEquals("aapsclient", clientFlavorFor("app.aaps.client"))
    }

    /** The identifiers the two targets actually ship with today. */
    @Test
    fun `the shipping identifiers map to the right clients`() {
        assertEquals("aapsclient", clientFlavorFor("app.aaps.client"))
        assertEquals("aapsclient2", clientFlavorFor("app.aaps.client2"))
    }

    /**
     * The targets used `info.nightscout.aapsclient` before the move to the `app.aaps` namespace.
     * Still recognised, so a device or build carrying the old identifier is not silently demoted to
     * client 1 - which is the bug this whole function exists to prevent.
     */
    @Test
    fun `the previous nightscout identifiers still work`() {
        assertEquals("aapsclient", clientFlavorFor("info.nightscout.aapsclient"))
        assertEquals("aapsclient2", clientFlavorFor("info.nightscout.aapsclient2"))
    }

    /**
     * The point of the whole change. Client 1's identifier is a prefix of this one, so a naive
     * "does it contain aapsclient" test answers client 1 here and the bug comes straight back.
     */
    @Test
    fun `the second target is client two and not client one`() {
        assertEquals("aapsclient2", clientFlavorFor("app.aaps.client2"))
    }

    @Test
    fun `a third client is recognised even though no target builds one yet`() {
        assertEquals("aapsclient3", clientFlavorFor("app.aaps.client3"))
    }

    /**
     * A test binary's identifier is its own, and an unrecognised one must still leave exactly one of
     * the three flags true. Client 1 is the safe answer: it is what the code said before.
     */
    @Test
    fun `an unknown identifier falls back to client one`() {
        assertEquals("aapsclient", clientFlavorFor("com.apple.dt.xctest.tool"))
        assertEquals("aapsclient", clientFlavorFor(""))
    }

    /** Only the tail decides, so a rename of the prefix cannot silently change the client number. */
    @Test
    fun `only the end of the identifier matters`() {
        assertEquals("aapsclient2", clientFlavorFor("com.example.fork.client2"))
    }
}
