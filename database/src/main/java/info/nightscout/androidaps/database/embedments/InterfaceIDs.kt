package info.nightscout.androidaps.database.embedments

data class InterfaceIDs(
        var nightscoutSystemId: String? = null,
        var nightscoutId: String? = null,
        var pumpType: PumpType? = null,
        var pumpSerial: String? = null,
        var pumpId: Long? = null,
        var startId: Long? = null,
        var endId: Long? = null
) {
    enum class PumpType {
        ACCU_CHEK_INSIGHT,
        ACCU_CHEK_COMBO,
        DANA_R,
        DANA_RS,
        MEDTRONIC,
        OMNIPOD
    }
}