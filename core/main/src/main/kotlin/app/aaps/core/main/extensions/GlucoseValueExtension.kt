package app.aaps.core.main.extensions

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.db.GlucoseUnit
import app.aaps.core.data.db.SourceSensor
import app.aaps.core.data.db.TrendArrow
import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.R
import app.aaps.database.entities.GlucoseValue
import org.json.JSONObject

fun GlucoseValue.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject()
        .put("device", sourceSensor.fromDb().text)
        .put("date", timestamp)
        .put("dateString", dateUtil.toISOString(timestamp))
        .put("isValid", isValid)
        .put("sgv", value)
        .put("direction", trendArrow.fromDb().text)
        .put("type", "sgv")
        .also { if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId) }

fun InMemoryGlucoseValue.valueToUnits(units: GlucoseUnit): Double =
    if (units == GlucoseUnit.MGDL) recalculated
    else recalculated * Constants.MGDL_TO_MMOLL

fun TrendArrow.directionToIcon(): Int =
    when (this) {
        TrendArrow.TRIPLE_DOWN     -> R.drawable.ic_invalid
        TrendArrow.DOUBLE_DOWN     -> R.drawable.ic_doubledown
        TrendArrow.SINGLE_DOWN     -> R.drawable.ic_singledown
        TrendArrow.FORTY_FIVE_DOWN -> R.drawable.ic_fortyfivedown
        TrendArrow.FLAT            -> R.drawable.ic_flat
        TrendArrow.FORTY_FIVE_UP   -> R.drawable.ic_fortyfiveup
        TrendArrow.SINGLE_UP       -> R.drawable.ic_singleup
        TrendArrow.DOUBLE_UP       -> R.drawable.ic_doubleup
        TrendArrow.TRIPLE_UP       -> R.drawable.ic_invalid
        TrendArrow.NONE            -> R.drawable.ic_invalid
    }

fun GlucoseValue.TrendArrow.fromDb(): TrendArrow =
    when (this) {
        GlucoseValue.TrendArrow.TRIPLE_DOWN     -> TrendArrow.TRIPLE_DOWN
        GlucoseValue.TrendArrow.DOUBLE_DOWN     -> TrendArrow.DOUBLE_DOWN
        GlucoseValue.TrendArrow.SINGLE_DOWN     -> TrendArrow.SINGLE_DOWN
        GlucoseValue.TrendArrow.FORTY_FIVE_DOWN -> TrendArrow.FORTY_FIVE_DOWN
        GlucoseValue.TrendArrow.FLAT            -> TrendArrow.FLAT
        GlucoseValue.TrendArrow.FORTY_FIVE_UP   -> TrendArrow.FORTY_FIVE_UP
        GlucoseValue.TrendArrow.SINGLE_UP       -> TrendArrow.SINGLE_UP
        GlucoseValue.TrendArrow.DOUBLE_UP       -> TrendArrow.DOUBLE_UP
        GlucoseValue.TrendArrow.TRIPLE_UP       -> TrendArrow.TRIPLE_UP
        GlucoseValue.TrendArrow.NONE            -> TrendArrow.NONE
    }

fun TrendArrow.toDb(): GlucoseValue.TrendArrow =
    when (this) {
        TrendArrow.TRIPLE_DOWN     -> GlucoseValue.TrendArrow.TRIPLE_DOWN
        TrendArrow.DOUBLE_DOWN     -> GlucoseValue.TrendArrow.DOUBLE_DOWN
        TrendArrow.SINGLE_DOWN     -> GlucoseValue.TrendArrow.SINGLE_DOWN
        TrendArrow.FORTY_FIVE_DOWN -> GlucoseValue.TrendArrow.FORTY_FIVE_DOWN
        TrendArrow.FLAT            -> GlucoseValue.TrendArrow.FLAT
        TrendArrow.FORTY_FIVE_UP   -> GlucoseValue.TrendArrow.FORTY_FIVE_UP
        TrendArrow.SINGLE_UP       -> GlucoseValue.TrendArrow.SINGLE_UP
        TrendArrow.DOUBLE_UP       -> GlucoseValue.TrendArrow.DOUBLE_UP
        TrendArrow.TRIPLE_UP       -> GlucoseValue.TrendArrow.TRIPLE_UP
        TrendArrow.NONE            -> GlucoseValue.TrendArrow.NONE
    }

fun GlucoseValue.SourceSensor.fromDb(): SourceSensor =
    when (this) {
        GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN     -> SourceSensor.DEXCOM_NATIVE_UNKNOWN
        GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE          -> SourceSensor.DEXCOM_G6_NATIVE
        GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE          -> SourceSensor.DEXCOM_G5_NATIVE
        GlucoseValue.SourceSensor.DEXCOM_G4_WIXEL           -> SourceSensor.DEXCOM_G4_WIXEL
        GlucoseValue.SourceSensor.DEXCOM_G4_XBRIDGE         -> SourceSensor.DEXCOM_G4_XBRIDGE
        GlucoseValue.SourceSensor.DEXCOM_G4_NATIVE          -> SourceSensor.DEXCOM_G4_NATIVE
        GlucoseValue.SourceSensor.MEDTRUM_A6                -> SourceSensor.MEDTRUM_A6
        GlucoseValue.SourceSensor.DEXCOM_G4_NET             -> SourceSensor.DEXCOM_G4_NET
        GlucoseValue.SourceSensor.DEXCOM_G4_NET_XBRIDGE     -> SourceSensor.DEXCOM_G4_NET_XBRIDGE
        GlucoseValue.SourceSensor.DEXCOM_G4_NET_CLASSIC     -> SourceSensor.DEXCOM_G4_NET_CLASSIC
        GlucoseValue.SourceSensor.DEXCOM_G5_XDRIP           -> SourceSensor.DEXCOM_G5_XDRIP
        GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP    -> SourceSensor.DEXCOM_G6_NATIVE_XDRIP
        GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE_XDRIP    -> SourceSensor.DEXCOM_G5_NATIVE_XDRIP
        GlucoseValue.SourceSensor.DEXCOM_G6_G5_NATIVE_XDRIP -> SourceSensor.DEXCOM_G6_G5_NATIVE_XDRIP
        GlucoseValue.SourceSensor.LIBRE_1_OTHER             -> SourceSensor.LIBRE_1_OTHER
        GlucoseValue.SourceSensor.LIBRE_1_NET               -> SourceSensor.LIBRE_1_NET
        GlucoseValue.SourceSensor.LIBRE_1_BLUE              -> SourceSensor.LIBRE_1_BLUE
        GlucoseValue.SourceSensor.LIBRE_1_PL                -> SourceSensor.LIBRE_1_PL
        GlucoseValue.SourceSensor.LIBRE_1_BLUCON            -> SourceSensor.LIBRE_1_BLUCON
        GlucoseValue.SourceSensor.LIBRE_1_TOMATO            -> SourceSensor.LIBRE_1_TOMATO
        GlucoseValue.SourceSensor.LIBRE_1_RF                -> SourceSensor.LIBRE_1_RF
        GlucoseValue.SourceSensor.LIBRE_1_LIMITTER          -> SourceSensor.LIBRE_1_LIMITTER
        GlucoseValue.SourceSensor.LIBRE_1_BUBBLE            -> SourceSensor.LIBRE_1_BUBBLE
        GlucoseValue.SourceSensor.LIBRE_1_ATOM              -> SourceSensor.LIBRE_1_ATOM
        GlucoseValue.SourceSensor.LIBRE_1_GLIMP             -> SourceSensor.LIBRE_1_GLIMP
        GlucoseValue.SourceSensor.LIBRE_2_NATIVE            -> SourceSensor.LIBRE_2_NATIVE
        GlucoseValue.SourceSensor.POCTECH_NATIVE            -> SourceSensor.POCTECH_NATIVE
        GlucoseValue.SourceSensor.GLUNOVO_NATIVE            -> SourceSensor.GLUNOVO_NATIVE
        GlucoseValue.SourceSensor.INTELLIGO_NATIVE          -> SourceSensor.INTELLIGO_NATIVE
        GlucoseValue.SourceSensor.MM_600_SERIES             -> SourceSensor.MM_600_SERIES
        GlucoseValue.SourceSensor.EVERSENSE                 -> SourceSensor.EVERSENSE
        GlucoseValue.SourceSensor.AIDEX                     -> SourceSensor.AIDEX
        GlucoseValue.SourceSensor.RANDOM                    -> SourceSensor.RANDOM
        GlucoseValue.SourceSensor.UNKNOWN                   -> SourceSensor.UNKNOWN

        GlucoseValue.SourceSensor.IOB_PREDICTION            -> SourceSensor.IOB_PREDICTION
        GlucoseValue.SourceSensor.A_COB_PREDICTION          -> SourceSensor.A_COB_PREDICTION
        GlucoseValue.SourceSensor.COB_PREDICTION            -> SourceSensor.COB_PREDICTION
        GlucoseValue.SourceSensor.UAM_PREDICTION            -> SourceSensor.UAM_PREDICTION
        GlucoseValue.SourceSensor.ZT_PREDICTION             -> SourceSensor.ZT_PREDICTION
    }

fun SourceSensor.toDb(): GlucoseValue.SourceSensor =
    when (this) {
        SourceSensor.DEXCOM_NATIVE_UNKNOWN     -> GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN
        SourceSensor.DEXCOM_G6_NATIVE          -> GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE
        SourceSensor.DEXCOM_G5_NATIVE          -> GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE
        SourceSensor.DEXCOM_G4_WIXEL           -> GlucoseValue.SourceSensor.DEXCOM_G4_WIXEL
        SourceSensor.DEXCOM_G4_XBRIDGE         -> GlucoseValue.SourceSensor.DEXCOM_G4_XBRIDGE
        SourceSensor.DEXCOM_G4_NATIVE          -> GlucoseValue.SourceSensor.DEXCOM_G4_NATIVE
        SourceSensor.MEDTRUM_A6                -> GlucoseValue.SourceSensor.MEDTRUM_A6
        SourceSensor.DEXCOM_G4_NET             -> GlucoseValue.SourceSensor.DEXCOM_G4_NET
        SourceSensor.DEXCOM_G4_NET_XBRIDGE     -> GlucoseValue.SourceSensor.DEXCOM_G4_NET_XBRIDGE
        SourceSensor.DEXCOM_G4_NET_CLASSIC     -> GlucoseValue.SourceSensor.DEXCOM_G4_NET_CLASSIC
        SourceSensor.DEXCOM_G5_XDRIP           -> GlucoseValue.SourceSensor.DEXCOM_G5_XDRIP
        SourceSensor.DEXCOM_G6_NATIVE_XDRIP    -> GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP
        SourceSensor.DEXCOM_G5_NATIVE_XDRIP    -> GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE_XDRIP
        SourceSensor.DEXCOM_G6_G5_NATIVE_XDRIP -> GlucoseValue.SourceSensor.DEXCOM_G6_G5_NATIVE_XDRIP
        SourceSensor.LIBRE_1_OTHER             -> GlucoseValue.SourceSensor.LIBRE_1_OTHER
        SourceSensor.LIBRE_1_NET               -> GlucoseValue.SourceSensor.LIBRE_1_NET
        SourceSensor.LIBRE_1_BLUE              -> GlucoseValue.SourceSensor.LIBRE_1_BLUE
        SourceSensor.LIBRE_1_PL                -> GlucoseValue.SourceSensor.LIBRE_1_PL
        SourceSensor.LIBRE_1_BLUCON            -> GlucoseValue.SourceSensor.LIBRE_1_BLUCON
        SourceSensor.LIBRE_1_TOMATO            -> GlucoseValue.SourceSensor.LIBRE_1_TOMATO
        SourceSensor.LIBRE_1_RF                -> GlucoseValue.SourceSensor.LIBRE_1_RF
        SourceSensor.LIBRE_1_LIMITTER          -> GlucoseValue.SourceSensor.LIBRE_1_LIMITTER
        SourceSensor.LIBRE_1_BUBBLE            -> GlucoseValue.SourceSensor.LIBRE_1_BUBBLE
        SourceSensor.LIBRE_1_ATOM              -> GlucoseValue.SourceSensor.LIBRE_1_ATOM
        SourceSensor.LIBRE_1_GLIMP             -> GlucoseValue.SourceSensor.LIBRE_1_GLIMP
        SourceSensor.LIBRE_2_NATIVE            -> GlucoseValue.SourceSensor.LIBRE_2_NATIVE
        SourceSensor.POCTECH_NATIVE            -> GlucoseValue.SourceSensor.POCTECH_NATIVE
        SourceSensor.GLUNOVO_NATIVE            -> GlucoseValue.SourceSensor.GLUNOVO_NATIVE
        SourceSensor.INTELLIGO_NATIVE          -> GlucoseValue.SourceSensor.INTELLIGO_NATIVE
        SourceSensor.MM_600_SERIES             -> GlucoseValue.SourceSensor.MM_600_SERIES
        SourceSensor.EVERSENSE                 -> GlucoseValue.SourceSensor.EVERSENSE
        SourceSensor.AIDEX                     -> GlucoseValue.SourceSensor.AIDEX
        SourceSensor.RANDOM                    -> GlucoseValue.SourceSensor.RANDOM
        SourceSensor.UNKNOWN                   -> GlucoseValue.SourceSensor.UNKNOWN

        SourceSensor.IOB_PREDICTION            -> GlucoseValue.SourceSensor.IOB_PREDICTION
        SourceSensor.A_COB_PREDICTION          -> GlucoseValue.SourceSensor.A_COB_PREDICTION
        SourceSensor.COB_PREDICTION            -> GlucoseValue.SourceSensor.COB_PREDICTION
        SourceSensor.UAM_PREDICTION            -> GlucoseValue.SourceSensor.UAM_PREDICTION
        SourceSensor.ZT_PREDICTION             -> GlucoseValue.SourceSensor.ZT_PREDICTION
    }