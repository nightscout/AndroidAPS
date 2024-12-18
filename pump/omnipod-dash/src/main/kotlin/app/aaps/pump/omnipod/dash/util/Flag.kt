package app.aaps.pump.omnipod.dash.util

class Flag(var value: Int = 0) {

    fun set(idx: Byte, set: Boolean) {
        val mask = 1 shl (7 - idx)
        if (!set)
            return
        value = value or mask
    }

    fun get(idx: Byte): Int {
        val mask = 1 shl (7 - idx)
        if (value and mask == 0) {
            return 0
        }
        return 1
    }
}
