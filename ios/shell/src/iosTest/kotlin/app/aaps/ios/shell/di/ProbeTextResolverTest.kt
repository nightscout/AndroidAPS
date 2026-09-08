package app.aaps.ios.shell.di

import app.aaps.core.keys.interfaces.TextRef
import kotlin.test.Test
import kotlin.test.assertEquals

/** The stand in resolver has to answer every [TextRef] shape, since a miss would throw at runtime. */
class ProbeTextResolverTest {

    @Test
    fun `it resolves each kind of reference`() {
        assertEquals("hello", ProbeTextResolver.gs(TextRef.Literal("hello")))
        assertEquals("some_name", ProbeTextResolver.gs(TextRef.Named(owner = "core", name = "some_name")))
        assertEquals("res:7", ProbeTextResolver.gs(TextRef.AndroidRes(id = 7)))
    }

    @Test
    fun `arguments do not change which reference is resolved`() {
        assertEquals("hello", ProbeTextResolver.gs(TextRef.Literal("hello"), 1, 2))
        assertEquals("hello", ProbeTextResolver.gsNotLocalised(TextRef.Literal("hello")))
    }
}
