package app.aaps.pump.diaconn.emulator

/**
 * The 12 data bytes of a record of the pump log: the time, one byte with the type (top 2 bits) and the kind (low 6
 * bits), the fields of the kind, then 0xFF up to 12 bytes.
 *
 * The time is the wall clock of the pump written as seconds since 1970 as if it were UTC. A real G8 (2026-11-11) stopped
 * its insulin at 18:55:12 local time and logged `10 ba f4 6a`, which is 2026-11-11 18:55:12 read as UTC.
 */
internal object DiaconnLog {

    const val SIZE = 12

    const val RESET_SYS = 0x01
    const val SUSPEND = 0x03
    const val SUSPEND_RELEASE = 0x04
    const val NORMAL_SUCCESS = 0x0A
    const val NORMAL_FAIL = 0x0B
    const val SET_SQUARE = 0x0C
    const val SQUARE_SUCCESS = 0x0D
    const val SQUARE_FAIL = 0x0E
    const val TB_START = 0x12
    const val TB_STOP = 0x13
    const val CHANGE_TUBE = 0x18
    const val CHANGE_INJECTOR = 0x1A
    const val CHANGE_NEEDLE = 0x1C
    const val ALARM_BATTERY = 0x28
    const val ALARM_BLOCK = 0x29
    const val ALARM_SHORTAGE = 0x2A
    const val HOUR_BASAL = 0x2C
    const val DAY_BASAL = 0x2E
    const val DAY = 0x2F

    /**
     * Real G8 (2026-11-11): type 2 on a temp basal stop (0x93), type 1 on a stop and a start of the insulin (0x43, 0x44).
     * UNVERIFIED: the type of every other kind; type 2 for a temp basal start, as for its stop, type 1 for the rest. The
     * driver reads only the kind.
     */
    private fun type(kind: Int): Int = if (kind == TB_START || kind == TB_STOP) 2 else 1

    /** A record of [kind] at [seconds] (see the class), with the fields [fields] writes after the kind. */
    fun record(seconds: Long, kind: Int, fields: PacketData.() -> Unit): ByteArray {
        val data = PacketData().u32(seconds).u8((type(kind) shl 6) or kind).apply(fields).bytes()
        require(data.size <= SIZE) { "a log record of kind $kind has ${data.size} bytes" }
        return ByteArray(SIZE) { if (it < data.size) data[it] else 0xFF.toByte() }
    }
}
