package info.nightscout.androidaps.database.embedments

data class InterfaceIDs(
    var nightscoutSystemId: String? = null,
    var nightscoutId: String? = null,
    var pumpType: PumpType? = null, // if == USER pumpSerial & pumpId can be null
    var pumpSerial: String? = null,
    var pumpId: Long? = null,
    var startId: Long? = null,
    var endId: Long? = null
) {

    enum class PumpType {
        USER,
        VIRTUAL_PUMP,
        ACCU_CHEK_INSIGHT,
        ACCU_CHEK_COMBO,
        DANA_R,
        DANA_RV2,
        DANA_RS,
        MEDTRONIC,
        OMNIPOD_EROS,
        OMNIPOD_DASH
    }
}