package app.aaps.pump.medtrum.extension

fun Boolean.toByte(): Byte {
    return if (this == true)
        0x1
    else
        0x0
}
