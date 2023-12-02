package info.nightscout.pump.medtrum.extension

/** Extensions for different types of conversions needed when doing stuff with bytes */ 
fun Int.toByteArray(byteLength: Int): ByteArray {
    val bytes = ByteArray(byteLength)
    for (i in 0 until byteLength) {
        bytes[i] = (this shr (i * 8) and 0xFF).toByte()
    }
    return bytes
}
