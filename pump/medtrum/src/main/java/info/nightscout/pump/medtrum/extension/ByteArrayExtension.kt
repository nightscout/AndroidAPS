package info.nightscout.pump.medtrum.extension

/** Extensions for different types of conversions needed when doing stuff with bytes */ 
fun ByteArray.toLong(): Long {
    require(this.size <= 8) {
        "Array size must be <= 8 for 'toLong' conversion operation"
    }
    var result = 0L
    for (i in this.indices) {
        val byte = this[i]
        val shifted = (byte.toInt() and 0xFF).toLong() shl 8 * i
        result = result or shifted
    }
    return result
}

fun ByteArray.toInt(): Int {
    require(this.size <= 4) {
        "Array size must be <= 4 for 'toInt' conversion operation"
    }
    var result = 0
    for (i in this.indices) {
        val byte = this[i]
        val shifted = (byte.toInt() and 0xFF) shl 8 * i
        result = result or shifted
    }
    return result
}

fun ByteArray.toFloat(): Float {
    require(this.size == 4) {
        "Array size must be == 4 for 'toFloat' conversion operation"
    }
    var asInt = 0
    for (i in this.indices) {
        val byte = this[i]
        val shifted = (byte.toInt() and 0xFF) shl 8 * i
        asInt = asInt or shifted
    }
    return Float.fromBits(asInt)
}
