package app.aaps.pump.danars

import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danars.comm.DanaRSPacket
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.kotlin.whenever

open class DanaRSTestBase : TestBaseWithProfile() {

    @Mock lateinit var uiInteraction: UiInteraction

    lateinit var danaPump: DanaPump

    @BeforeEach
    fun prepare() {
        whenever(rh.gs(ArgumentMatchers.anyInt())).thenReturn("AnyString")
    }

    fun createArray(length: Int, fillWith: Byte): ByteArray {
        val ret = ByteArray(length)
        for (i in 0 until length) {
            ret[i] = fillWith
        }
        return ret
    }

    fun createArray(length: Int, fillWith: Double): Array<Double> {
        val ret = Array(length) { 0.0 }
        for (i in 0 until length) {
            ret[i] = fillWith
        }
        return ret
    }

    @Suppress("unused")
    fun putIntToArray(array: ByteArray, position: Int, value: Int): ByteArray {
        array[DanaRSPacket.DATA_START + position] = (value and 0xFF).toByte()
        array[DanaRSPacket.DATA_START + position + 1] = ((value and 0xFF00) shr 8).toByte()
        return array
    }

    fun putByteToArray(array: ByteArray, position: Int, value: Byte): ByteArray {
        array[DanaRSPacket.DATA_START + position] = value
        return array
    }

    @BeforeEach
    fun setup() {
        danaPump = DanaPump(aapsLogger, preferences, dateUtil, decimalFormatter, profileStoreProvider)
    }
}