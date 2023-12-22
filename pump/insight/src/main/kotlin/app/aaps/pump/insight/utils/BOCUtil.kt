package app.aaps.pump.insight.utils

import kotlin.experimental.and

object BOCUtil {

    fun parseBOC(b: Byte): Int {
        return ((b and 0xF0.toByte()).toInt() shr 4) * 10 + (b and 0x0F)
    }
}