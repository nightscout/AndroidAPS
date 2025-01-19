package app.aaps.pump.common.hw.rileylink.ble.defs

enum class RFSpyRLResponse(val value: Byte) {
    // 0xaa == timeout
    // 0xbb == interrupted
    // 0xcc == zero-data
    // 0xdd == success
    // 0x11 == invalidParam
    // 0x22 == unknownCommand
    Invalid(0),  // default, just fail
    Timeout(0xAA.toByte()),
    Interrupted(0xBB.toByte()),
    ZeroData(0xCC.toByte()),
    Success(0xDD.toByte()),
    OldSuccess(0x01),
    InvalidParam(0x11),
    UnknownCommand(0x22),
    ;

    companion object {

        fun fromByte(input: Byte): RFSpyRLResponse? = entries.find { it.value == input }
    }
}
