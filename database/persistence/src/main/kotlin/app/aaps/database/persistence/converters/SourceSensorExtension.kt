package app.aaps.database.persistence.converters

import app.aaps.core.data.model.SourceSensor
import app.aaps.database.entities.GlucoseValue

fun GlucoseValue.SourceSensor.fromDb(): SourceSensor =
    when (this) {
        GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN  -> SourceSensor.DEXCOM_NATIVE_UNKNOWN
        GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE       -> SourceSensor.DEXCOM_G6_NATIVE
        GlucoseValue.SourceSensor.DEXCOM_G7_NATIVE       -> SourceSensor.DEXCOM_G7_NATIVE
        GlucoseValue.SourceSensor.MEDTRUM_A6             -> SourceSensor.MEDTRUM_A6
        GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP -> SourceSensor.DEXCOM_G6_NATIVE_XDRIP
        GlucoseValue.SourceSensor.DEXCOM_G7_NATIVE_XDRIP -> SourceSensor.DEXCOM_G7_NATIVE_XDRIP
        GlucoseValue.SourceSensor.DEXCOM_G7_XDRIP        -> SourceSensor.DEXCOM_G7_XDRIP
        GlucoseValue.SourceSensor.LIBRE_1_OTHER          -> SourceSensor.LIBRE_1_OTHER
        GlucoseValue.SourceSensor.LIBRE_1_NET            -> SourceSensor.LIBRE_1_NET
        GlucoseValue.SourceSensor.LIBRE_1_BLUE           -> SourceSensor.LIBRE_1_BLUE
        GlucoseValue.SourceSensor.LIBRE_1_PL             -> SourceSensor.LIBRE_1_PL
        GlucoseValue.SourceSensor.LIBRE_1_BLUCON         -> SourceSensor.LIBRE_1_BLUCON
        GlucoseValue.SourceSensor.LIBRE_1_TOMATO         -> SourceSensor.LIBRE_1_TOMATO
        GlucoseValue.SourceSensor.LIBRE_1_RF             -> SourceSensor.LIBRE_1_RF
        GlucoseValue.SourceSensor.LIBRE_1_LIMITTER       -> SourceSensor.LIBRE_1_LIMITTER
        GlucoseValue.SourceSensor.LIBRE_1_BUBBLE         -> SourceSensor.LIBRE_1_BUBBLE
        GlucoseValue.SourceSensor.LIBRE_1_ATOM           -> SourceSensor.LIBRE_1_ATOM
        GlucoseValue.SourceSensor.LIBRE_1_GLIMP          -> SourceSensor.LIBRE_1_GLIMP
        GlucoseValue.SourceSensor.LIBRE_2                -> SourceSensor.LIBRE_2
        GlucoseValue.SourceSensor.LIBRE_2_NATIVE         -> SourceSensor.LIBRE_2_NATIVE
        GlucoseValue.SourceSensor.LIBRE_3                -> SourceSensor.LIBRE_3
        GlucoseValue.SourceSensor.POCTECH_NATIVE         -> SourceSensor.POCTECH_NATIVE
        GlucoseValue.SourceSensor.GLUNOVO_NATIVE         -> SourceSensor.GLUNOVO_NATIVE
        GlucoseValue.SourceSensor.INTELLIGO_NATIVE       -> SourceSensor.INTELLIGO_NATIVE
        GlucoseValue.SourceSensor.MM_600_SERIES          -> SourceSensor.MM_600_SERIES
        GlucoseValue.SourceSensor.EVERSENSE              -> SourceSensor.EVERSENSE
        GlucoseValue.SourceSensor.AIDEX                  -> SourceSensor.AIDEX
        GlucoseValue.SourceSensor.RANDOM                 -> SourceSensor.RANDOM
        GlucoseValue.SourceSensor.UNKNOWN                -> SourceSensor.UNKNOWN
        GlucoseValue.SourceSensor.OTTAI                  -> SourceSensor.OTTAI
        GlucoseValue.SourceSensor.SYAI_TAG               -> SourceSensor.SYAI_TAG
        GlucoseValue.SourceSensor.SIBIONIC -> SourceSensor.SIBIONIC
        GlucoseValue.SourceSensor.SINO     -> SourceSensor.SINO

        GlucoseValue.SourceSensor.IOB_PREDICTION         -> SourceSensor.IOB_PREDICTION
        GlucoseValue.SourceSensor.A_COB_PREDICTION       -> SourceSensor.A_COB_PREDICTION
        GlucoseValue.SourceSensor.COB_PREDICTION         -> SourceSensor.COB_PREDICTION
        GlucoseValue.SourceSensor.UAM_PREDICTION         -> SourceSensor.UAM_PREDICTION
        GlucoseValue.SourceSensor.ZT_PREDICTION          -> SourceSensor.ZT_PREDICTION
    }

fun SourceSensor.toDb(): GlucoseValue.SourceSensor =
    when (this) {
        SourceSensor.DEXCOM_NATIVE_UNKNOWN  -> GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN
        SourceSensor.DEXCOM_G6_NATIVE       -> GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE
        SourceSensor.DEXCOM_G7_NATIVE       -> GlucoseValue.SourceSensor.DEXCOM_G7_NATIVE
        SourceSensor.MEDTRUM_A6             -> GlucoseValue.SourceSensor.MEDTRUM_A6
        SourceSensor.DEXCOM_G6_NATIVE_XDRIP -> GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP
        SourceSensor.DEXCOM_G7_NATIVE_XDRIP -> GlucoseValue.SourceSensor.DEXCOM_G7_NATIVE_XDRIP
        SourceSensor.DEXCOM_G7_XDRIP        -> GlucoseValue.SourceSensor.DEXCOM_G7_XDRIP
        SourceSensor.LIBRE_1_OTHER          -> GlucoseValue.SourceSensor.LIBRE_1_OTHER
        SourceSensor.LIBRE_1_NET            -> GlucoseValue.SourceSensor.LIBRE_1_NET
        SourceSensor.LIBRE_1_BLUE           -> GlucoseValue.SourceSensor.LIBRE_1_BLUE
        SourceSensor.LIBRE_1_PL             -> GlucoseValue.SourceSensor.LIBRE_1_PL
        SourceSensor.LIBRE_1_BLUCON         -> GlucoseValue.SourceSensor.LIBRE_1_BLUCON
        SourceSensor.LIBRE_1_TOMATO         -> GlucoseValue.SourceSensor.LIBRE_1_TOMATO
        SourceSensor.LIBRE_1_RF             -> GlucoseValue.SourceSensor.LIBRE_1_RF
        SourceSensor.LIBRE_1_LIMITTER       -> GlucoseValue.SourceSensor.LIBRE_1_LIMITTER
        SourceSensor.LIBRE_1_BUBBLE         -> GlucoseValue.SourceSensor.LIBRE_1_BUBBLE
        SourceSensor.LIBRE_1_ATOM           -> GlucoseValue.SourceSensor.LIBRE_1_ATOM
        SourceSensor.LIBRE_1_GLIMP          -> GlucoseValue.SourceSensor.LIBRE_1_GLIMP
        SourceSensor.LIBRE_2                -> GlucoseValue.SourceSensor.LIBRE_2
        SourceSensor.LIBRE_2_NATIVE         -> GlucoseValue.SourceSensor.LIBRE_2_NATIVE
        SourceSensor.LIBRE_3                -> GlucoseValue.SourceSensor.LIBRE_3
        SourceSensor.POCTECH_NATIVE         -> GlucoseValue.SourceSensor.POCTECH_NATIVE
        SourceSensor.GLUNOVO_NATIVE         -> GlucoseValue.SourceSensor.GLUNOVO_NATIVE
        SourceSensor.INTELLIGO_NATIVE       -> GlucoseValue.SourceSensor.INTELLIGO_NATIVE
        SourceSensor.MM_600_SERIES          -> GlucoseValue.SourceSensor.MM_600_SERIES
        SourceSensor.EVERSENSE              -> GlucoseValue.SourceSensor.EVERSENSE
        SourceSensor.AIDEX                  -> GlucoseValue.SourceSensor.AIDEX
        SourceSensor.RANDOM                 -> GlucoseValue.SourceSensor.RANDOM
        SourceSensor.UNKNOWN                -> GlucoseValue.SourceSensor.UNKNOWN
        SourceSensor.OTTAI                  -> GlucoseValue.SourceSensor.OTTAI
        SourceSensor.SYAI_TAG               -> GlucoseValue.SourceSensor.SYAI_TAG
        SourceSensor.SIBIONIC -> GlucoseValue.SourceSensor.SIBIONIC
        SourceSensor.SINO     -> GlucoseValue.SourceSensor.SINO

        SourceSensor.IOB_PREDICTION         -> GlucoseValue.SourceSensor.IOB_PREDICTION
        SourceSensor.A_COB_PREDICTION       -> GlucoseValue.SourceSensor.A_COB_PREDICTION
        SourceSensor.COB_PREDICTION         -> GlucoseValue.SourceSensor.COB_PREDICTION
        SourceSensor.UAM_PREDICTION         -> GlucoseValue.SourceSensor.UAM_PREDICTION
        SourceSensor.ZT_PREDICTION          -> GlucoseValue.SourceSensor.ZT_PREDICTION
    }
