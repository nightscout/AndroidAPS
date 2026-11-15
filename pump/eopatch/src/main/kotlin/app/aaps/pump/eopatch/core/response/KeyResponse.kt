package app.aaps.pump.eopatch.core.response

class KeyResponse(
    val publicKey: ByteArray?,
    val code: Int
) : BaseResponse() {

    val sequence: Int get() = code

    companion object {

        fun create(key: ByteArray, code: Int) = KeyResponse(key, code)

        fun create(s1: Byte, s2: Byte): KeyResponse {
            val seq = ((s1.toInt() and 0x7F) shl 8) or (s2.toInt() and 0xFF)
            return KeyResponse(null, seq)
        }
    }
}
