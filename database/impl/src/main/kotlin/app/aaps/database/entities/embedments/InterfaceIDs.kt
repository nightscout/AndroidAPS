package app.aaps.database.entities.embedments

import androidx.room.Ignore

data class InterfaceIDs @Ignore constructor(
    var nightscoutSystemId: String? = null,
    var nightscoutId: String? = null,
    var pumpType: PumpType? = null, // if == USER pumpSerial & pumpId can be null
    var pumpSerial: String? = null,
    var temporaryId: Long? = null, // temporary id for pump synchronization, when pump id is not available
    var pumpId: Long? = null,
    var startId: Long? = null,
    var endId: Long? = null
) {

    // Along with @Ignore main constructor eliminate kotlin 1.9 + room warning
    constructor() : this(nightscoutSystemId = null, nightscoutId = null, pumpType = null, pumpSerial = null, temporaryId = null, pumpId = null, startId = null, endId = null)

    enum class PumpType {
        GENERIC_AAPS,
        CELLNOVO,
        ACCU_CHEK_COMBO,
        ACCU_CHEK_SPIRIT,
        ACCU_CHEK_INSIGHT,
        ACCU_CHEK_INSIGHT_BLUETOOTH,
        ACCU_CHEK_SOLO,
        ANIMAS_VIBE,
        ANIMAS_PING,
        DANA_R,
        DANA_R_KOREAN,
        DANA_RV2,
        DANA_I,
        DANA_RS,
        DANA_RS_KOREAN,
        OMNIPOD_EROS,
        OMNIPOD_DASH,
        MEDTRONIC_512_517,
        MEDTRONIC_515_715,
        MEDTRONIC_522_722,
        MEDTRONIC_523_723_REVEL,
        MEDTRONIC_554_754_VEO,
        MEDTRONIC_640G,
        TANDEM_T_SLIM,
        TANDEM_T_FLEX,
        TANDEM_T_SLIM_G4,
        TANDEM_T_SLIM_X2,
        YPSOPUMP,
        MDI,
        DIACONN_G8,
        EOPATCH2,
        MEDTRUM,
        MEDTRUM_300U,
        MEDTRUM_UNTESTED,
        USER,
        CACHE,
        EQUIL,
        APEX_TRUCARE_III;

        companion object {

            fun fromString(name: String?) = entries.firstOrNull { it.name == name }
        }
    }
}