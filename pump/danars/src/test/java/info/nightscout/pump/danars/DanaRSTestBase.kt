package info.nightscout.pump.danars

import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.interfaces.profile.Instantiator
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.dana.DanaPump
import info.nightscout.pump.danars.comm.DanaRSPacket
import info.nightscout.shared.sharedPreferences.SP
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito

open class DanaRSTestBase : TestBaseWithProfile() {

    @Mock lateinit var sp: SP
    @Mock lateinit var instantiator: Instantiator
    @Mock lateinit var uiInteraction: UiInteraction

    lateinit var danaPump: DanaPump

    @BeforeEach
    fun prepare() {
        Mockito.`when`(rh.gs(ArgumentMatchers.anyInt())).thenReturn("AnyString")
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
        danaPump = DanaPump(aapsLogger, sp, dateUtil, instantiator)
    }
}