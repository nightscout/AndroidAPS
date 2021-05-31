package info.nightscout.androidaps.plugins.pump.insight.utils

import info.nightscout.androidaps.TestBase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ByteBufTest : TestBase() {


    @Test
    fun `test length on creation`() {
        val sut = ByteBuf(15)
        assertEquals(0, sut.size) // not filled yet
        assertEquals(15, sut.bytes.size)
    }


}