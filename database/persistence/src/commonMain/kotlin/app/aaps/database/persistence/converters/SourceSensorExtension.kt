package app.aaps.database.persistence.converters

import app.aaps.core.data.model.SourceSensor
import app.aaps.database.entities.GlucoseValue

fun GlucoseValue.SourceSensor.fromDb(): SourceSensor =
    when (this) {
        GlucoseValue.SourceSensor.DEXCOM_UNKNOWN    -> SourceSensor.DEXCOM_UNKNOWN
        GlucoseValue.SourceSensor.DEXCOM_G6         -> SourceSensor.DEXCOM_G6
        GlucoseValue.SourceSensor.DEXCOM_G7         -> SourceSensor.DEXCOM_G7
        GlucoseValue.SourceSensor.LIBRE_1           -> SourceSensor.LIBRE_1
        GlucoseValue.SourceSensor.LIBRE_2           -> SourceSensor.LIBRE_2
        GlucoseValue.SourceSensor.LIBRE_3           -> SourceSensor.LIBRE_3
        GlucoseValue.SourceSensor.MEDTRUM_A6        -> SourceSensor.MEDTRUM_A6
        GlucoseValue.SourceSensor.MEDTRUM_UNKNOWN   -> SourceSensor.MEDTRUM_UNKNOWN
        GlucoseValue.SourceSensor.MM_600_SERIES     -> SourceSensor.MM_600_SERIES
        GlucoseValue.SourceSensor.MM_SIMPLERA       -> SourceSensor.MM_SIMPLERA
        GlucoseValue.SourceSensor.MM_UNKNOWN        -> SourceSensor.MM_UNKNOWN
        GlucoseValue.SourceSensor.SIBIONIC_UNKNOWN  -> SourceSensor.SIBIONIC_UNKNOWN
        GlucoseValue.SourceSensor.SIBIONIC_GS1      -> SourceSensor.SIBIONIC_GS1
        GlucoseValue.SourceSensor.SIBIONIC_GS3      -> SourceSensor.SIBIONIC_GS3
        GlucoseValue.SourceSensor.ACCU_CHEK         -> SourceSensor.ACCU_CHEK
        GlucoseValue.SourceSensor.CARESENS_AIR      -> SourceSensor.CARESENS_AIR
        GlucoseValue.SourceSensor.AIDEX             -> SourceSensor.AIDEX
        GlucoseValue.SourceSensor.AIDEX_X           -> SourceSensor.AIDEX_X
        GlucoseValue.SourceSensor.POCTECH_NATIVE    -> SourceSensor.POCTECH_NATIVE
        GlucoseValue.SourceSensor.GLUNOVO_NATIVE    -> SourceSensor.GLUNOVO_NATIVE
        GlucoseValue.SourceSensor.INTELLIGO_NATIVE  -> SourceSensor.INTELLIGO_NATIVE
        GlucoseValue.SourceSensor.SINOCARE          -> SourceSensor.SINOCARE
        GlucoseValue.SourceSensor.ANYTIME           -> SourceSensor.ANYTIME
        GlucoseValue.SourceSensor.GLUTEC            -> SourceSensor.GLUTEC
        GlucoseValue.SourceSensor.GLUPRO            -> SourceSensor.GLUPRO
        GlucoseValue.SourceSensor.EVERSENSE         -> SourceSensor.EVERSENSE
        GlucoseValue.SourceSensor.SYAI_TAG          -> SourceSensor.SYAI_TAG
        GlucoseValue.SourceSensor.INSTARA           -> SourceSensor.INSTARA
        GlucoseValue.SourceSensor.RANDOM            -> SourceSensor.RANDOM
        GlucoseValue.SourceSensor.UNKNOWN           -> SourceSensor.UNKNOWN

        GlucoseValue.SourceSensor.IOB_PREDICTION    -> SourceSensor.IOB_PREDICTION
        GlucoseValue.SourceSensor.A_COB_PREDICTION  -> SourceSensor.A_COB_PREDICTION
        GlucoseValue.SourceSensor.COB_PREDICTION    -> SourceSensor.COB_PREDICTION
        GlucoseValue.SourceSensor.UAM_PREDICTION    -> SourceSensor.UAM_PREDICTION
        GlucoseValue.SourceSensor.ZT_PREDICTION     -> SourceSensor.ZT_PREDICTION
    }

fun SourceSensor.toDb(): GlucoseValue.SourceSensor =
    when (this) {
        SourceSensor.DEXCOM_UNKNOWN    -> GlucoseValue.SourceSensor.DEXCOM_UNKNOWN
        SourceSensor.DEXCOM_G6         -> GlucoseValue.SourceSensor.DEXCOM_G6
        SourceSensor.DEXCOM_G7         -> GlucoseValue.SourceSensor.DEXCOM_G7
        SourceSensor.LIBRE_1           -> GlucoseValue.SourceSensor.LIBRE_1
        SourceSensor.LIBRE_2           -> GlucoseValue.SourceSensor.LIBRE_2
        SourceSensor.LIBRE_3           -> GlucoseValue.SourceSensor.LIBRE_3
        SourceSensor.MEDTRUM_A6        -> GlucoseValue.SourceSensor.MEDTRUM_A6
        SourceSensor.MEDTRUM_UNKNOWN   -> GlucoseValue.SourceSensor.MEDTRUM_UNKNOWN
        SourceSensor.MM_600_SERIES     -> GlucoseValue.SourceSensor.MM_600_SERIES
        SourceSensor.MM_SIMPLERA       -> GlucoseValue.SourceSensor.MM_SIMPLERA
        SourceSensor.MM_UNKNOWN        -> GlucoseValue.SourceSensor.MM_UNKNOWN
        SourceSensor.SIBIONIC_UNKNOWN  -> GlucoseValue.SourceSensor.SIBIONIC_UNKNOWN
        SourceSensor.SIBIONIC_GS1      -> GlucoseValue.SourceSensor.SIBIONIC_GS1
        SourceSensor.SIBIONIC_GS3      -> GlucoseValue.SourceSensor.SIBIONIC_GS3
        SourceSensor.ACCU_CHEK         -> GlucoseValue.SourceSensor.ACCU_CHEK
        SourceSensor.CARESENS_AIR      -> GlucoseValue.SourceSensor.CARESENS_AIR
        SourceSensor.AIDEX             -> GlucoseValue.SourceSensor.AIDEX
        SourceSensor.AIDEX_X           -> GlucoseValue.SourceSensor.AIDEX_X
        SourceSensor.POCTECH_NATIVE    -> GlucoseValue.SourceSensor.POCTECH_NATIVE
        SourceSensor.GLUNOVO_NATIVE    -> GlucoseValue.SourceSensor.GLUNOVO_NATIVE
        SourceSensor.INTELLIGO_NATIVE  -> GlucoseValue.SourceSensor.INTELLIGO_NATIVE
        SourceSensor.SINOCARE          -> GlucoseValue.SourceSensor.SINOCARE
        SourceSensor.ANYTIME           -> GlucoseValue.SourceSensor.ANYTIME
        SourceSensor.GLUTEC            -> GlucoseValue.SourceSensor.GLUTEC
        SourceSensor.GLUPRO            -> GlucoseValue.SourceSensor.GLUPRO
        SourceSensor.EVERSENSE         -> GlucoseValue.SourceSensor.EVERSENSE
        SourceSensor.SYAI_TAG          -> GlucoseValue.SourceSensor.SYAI_TAG
        SourceSensor.INSTARA           -> GlucoseValue.SourceSensor.INSTARA
        SourceSensor.RANDOM            -> GlucoseValue.SourceSensor.RANDOM
        SourceSensor.UNKNOWN           -> GlucoseValue.SourceSensor.UNKNOWN

        SourceSensor.IOB_PREDICTION    -> GlucoseValue.SourceSensor.IOB_PREDICTION
        SourceSensor.A_COB_PREDICTION  -> GlucoseValue.SourceSensor.A_COB_PREDICTION
        SourceSensor.COB_PREDICTION    -> GlucoseValue.SourceSensor.COB_PREDICTION
        SourceSensor.UAM_PREDICTION    -> GlucoseValue.SourceSensor.UAM_PREDICTION
        SourceSensor.ZT_PREDICTION     -> GlucoseValue.SourceSensor.ZT_PREDICTION
    }
