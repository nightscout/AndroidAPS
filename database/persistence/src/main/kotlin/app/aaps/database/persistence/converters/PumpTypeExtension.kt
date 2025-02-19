package app.aaps.database.persistence.converters

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.database.entities.embedments.InterfaceIDs

fun InterfaceIDs.PumpType.fromDb(): PumpType =
    when (this) {
        InterfaceIDs.PumpType.GENERIC_AAPS                -> PumpType.GENERIC_AAPS
        InterfaceIDs.PumpType.CELLNOVO                    -> PumpType.CELLNOVO
        InterfaceIDs.PumpType.ACCU_CHEK_COMBO             -> PumpType.ACCU_CHEK_COMBO
        InterfaceIDs.PumpType.ACCU_CHEK_SPIRIT            -> PumpType.ACCU_CHEK_SPIRIT
        InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT           -> PumpType.ACCU_CHEK_INSIGHT_VIRTUAL
        InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT_BLUETOOTH -> PumpType.ACCU_CHEK_INSIGHT
        InterfaceIDs.PumpType.ACCU_CHEK_SOLO              -> PumpType.ACCU_CHEK_SOLO
        InterfaceIDs.PumpType.ANIMAS_VIBE                 -> PumpType.ANIMAS_VIBE
        InterfaceIDs.PumpType.ANIMAS_PING                 -> PumpType.ANIMAS_PING
        InterfaceIDs.PumpType.DANA_R                      -> PumpType.DANA_R
        InterfaceIDs.PumpType.DANA_R_KOREAN               -> PumpType.DANA_R_KOREAN
        InterfaceIDs.PumpType.DANA_RS                     -> PumpType.DANA_RS
        InterfaceIDs.PumpType.DANA_RS_KOREAN              -> PumpType.DANA_RS_KOREAN
        InterfaceIDs.PumpType.DANA_RV2                    -> PumpType.DANA_RV2
        InterfaceIDs.PumpType.DANA_I                      -> PumpType.DANA_I
        InterfaceIDs.PumpType.OMNIPOD_EROS                -> PumpType.OMNIPOD_EROS
        InterfaceIDs.PumpType.OMNIPOD_DASH                -> PumpType.OMNIPOD_DASH
        InterfaceIDs.PumpType.MEDTRONIC_512_517           -> PumpType.MEDTRONIC_512_712
        InterfaceIDs.PumpType.MEDTRONIC_515_715           -> PumpType.MEDTRONIC_515_715
        InterfaceIDs.PumpType.MEDTRONIC_522_722           -> PumpType.MEDTRONIC_522_722
        InterfaceIDs.PumpType.MEDTRONIC_523_723_REVEL     -> PumpType.MEDTRONIC_523_723_REVEL
        InterfaceIDs.PumpType.MEDTRONIC_554_754_VEO       -> PumpType.MEDTRONIC_554_754_VEO
        InterfaceIDs.PumpType.MEDTRONIC_640G              -> PumpType.MEDTRONIC_640G
        InterfaceIDs.PumpType.TANDEM_T_SLIM               -> PumpType.TANDEM_T_SLIM
        InterfaceIDs.PumpType.TANDEM_T_SLIM_G4            -> PumpType.TANDEM_T_SLIM_G4
        InterfaceIDs.PumpType.TANDEM_T_FLEX               -> PumpType.TANDEM_T_FLEX
        InterfaceIDs.PumpType.TANDEM_T_SLIM_X2            -> PumpType.TANDEM_T_SLIM_X2
        InterfaceIDs.PumpType.YPSOPUMP                    -> PumpType.YPSOPUMP
        InterfaceIDs.PumpType.MDI                         -> PumpType.MDI
        InterfaceIDs.PumpType.USER                        -> PumpType.USER
        InterfaceIDs.PumpType.DIACONN_G8                  -> PumpType.DIACONN_G8
        InterfaceIDs.PumpType.EOPATCH2                    -> PumpType.EOFLOW_EOPATCH2
        InterfaceIDs.PumpType.MEDTRUM                     -> PumpType.MEDTRUM_NANO
        InterfaceIDs.PumpType.MEDTRUM_300U                -> PumpType.MEDTRUM_300U
        InterfaceIDs.PumpType.MEDTRUM_UNTESTED            -> PumpType.MEDTRUM_UNTESTED
        InterfaceIDs.PumpType.CACHE                       -> PumpType.CACHE
        InterfaceIDs.PumpType.EQUIL                       -> PumpType.EQUIL
        InterfaceIDs.PumpType.APEX_TRUCARE_III            -> PumpType.APEX_TRUCARE_III
    }

fun PumpType.toDb(): InterfaceIDs.PumpType =
    when (this) {
        PumpType.GENERIC_AAPS              -> InterfaceIDs.PumpType.GENERIC_AAPS
        PumpType.CELLNOVO                  -> InterfaceIDs.PumpType.CELLNOVO
        PumpType.ACCU_CHEK_COMBO           -> InterfaceIDs.PumpType.ACCU_CHEK_COMBO
        PumpType.ACCU_CHEK_SPIRIT          -> InterfaceIDs.PumpType.ACCU_CHEK_SPIRIT
        PumpType.ACCU_CHEK_INSIGHT_VIRTUAL -> InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT
        PumpType.ACCU_CHEK_INSIGHT         -> InterfaceIDs.PumpType.ACCU_CHEK_INSIGHT_BLUETOOTH
        PumpType.ACCU_CHEK_SOLO            -> InterfaceIDs.PumpType.ACCU_CHEK_SOLO
        PumpType.ANIMAS_VIBE               -> InterfaceIDs.PumpType.ANIMAS_VIBE
        PumpType.ANIMAS_PING               -> InterfaceIDs.PumpType.ANIMAS_PING
        PumpType.DANA_R                    -> InterfaceIDs.PumpType.DANA_R
        PumpType.DANA_R_KOREAN             -> InterfaceIDs.PumpType.DANA_R_KOREAN
        PumpType.DANA_RS                   -> InterfaceIDs.PumpType.DANA_RS
        PumpType.DANA_RS_KOREAN            -> InterfaceIDs.PumpType.DANA_RS_KOREAN
        PumpType.DANA_RV2                  -> InterfaceIDs.PumpType.DANA_RV2
        PumpType.DANA_I                    -> InterfaceIDs.PumpType.DANA_I
        PumpType.OMNIPOD_EROS              -> InterfaceIDs.PumpType.OMNIPOD_EROS
        PumpType.OMNIPOD_DASH              -> InterfaceIDs.PumpType.OMNIPOD_DASH
        PumpType.MEDTRONIC_512_712         -> InterfaceIDs.PumpType.MEDTRONIC_512_517
        PumpType.MEDTRONIC_515_715         -> InterfaceIDs.PumpType.MEDTRONIC_515_715
        PumpType.MEDTRONIC_522_722         -> InterfaceIDs.PumpType.MEDTRONIC_522_722
        PumpType.MEDTRONIC_523_723_REVEL   -> InterfaceIDs.PumpType.MEDTRONIC_523_723_REVEL
        PumpType.MEDTRONIC_554_754_VEO     -> InterfaceIDs.PumpType.MEDTRONIC_554_754_VEO
        PumpType.MEDTRONIC_640G            -> InterfaceIDs.PumpType.MEDTRONIC_640G
        PumpType.TANDEM_T_SLIM             -> InterfaceIDs.PumpType.TANDEM_T_SLIM
        PumpType.TANDEM_T_SLIM_G4          -> InterfaceIDs.PumpType.TANDEM_T_SLIM_G4
        PumpType.TANDEM_T_FLEX             -> InterfaceIDs.PumpType.TANDEM_T_FLEX
        PumpType.TANDEM_T_SLIM_X2          -> InterfaceIDs.PumpType.TANDEM_T_SLIM_X2
        PumpType.YPSOPUMP                  -> InterfaceIDs.PumpType.YPSOPUMP
        PumpType.MDI                       -> InterfaceIDs.PumpType.MDI
        PumpType.USER                      -> InterfaceIDs.PumpType.USER
        PumpType.DIACONN_G8                -> InterfaceIDs.PumpType.DIACONN_G8
        PumpType.EOFLOW_EOPATCH2           -> InterfaceIDs.PumpType.EOPATCH2
        PumpType.MEDTRUM_NANO              -> InterfaceIDs.PumpType.MEDTRUM
        PumpType.MEDTRUM_300U              -> InterfaceIDs.PumpType.MEDTRUM_300U
        PumpType.MEDTRUM_UNTESTED          -> InterfaceIDs.PumpType.MEDTRUM_UNTESTED
        PumpType.CACHE                     -> InterfaceIDs.PumpType.CACHE
        PumpType.EQUIL                     -> InterfaceIDs.PumpType.EQUIL
        PumpType.APEX_TRUCARE_III          -> InterfaceIDs.PumpType.APEX_TRUCARE_III

    }
