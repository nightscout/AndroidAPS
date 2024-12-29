package app.aaps.pump.omnipod.dash.driver.pod.definition

import java.io.Serializable
import kotlin.experimental.and

class ProgramReminder(
    private val atStart: Boolean,
    private val atEnd: Boolean,
    private val atInterval: Byte
) : Encodable, Serializable {

    override val encoded: ByteArray
        get() = byteArrayOf(
            (
                (if (atStart) 1 else 0) shl 7
                    or ((if (atEnd) 1 else 0) shl 6)
                    or ((atInterval and 0x3f).toInt())
                ).toByte()
        )

    override fun toString(): String {
        return "ProgramReminder(atStart=$atStart, atEnd=$atEnd, atInterval=$atInterval)"
    }
}
