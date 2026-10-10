package app.aaps.pump.eopatch.core.ble

object MacVerifier {

    private const val ADDRESS_LENGTH = 17

    fun isValid(address: String?): Boolean {
        if (address == null || address.length != ADDRESS_LENGTH) return false
        for (i in 0 until ADDRESS_LENGTH) {
            val c = address[i]
            when (i % 3) {
                0, 1 -> if (!c.isHexDigit()) return false
                2    -> if (c != ':') return false
            }
        }
        return true
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'A'..'F' || this in 'a'..'f'
}
