import com.google.gson.annotations.SerializedName
import info.nightscout.androidaps.database.entities.GlucoseValue

enum class SourceSensorDomain(val text : String) {
    @SerializedName("AndroidAPS-Dexcom")
    DEXCOM_NATIVE_UNKNOWN("AndroidAPS-Dexcom"),
    @SerializedName("AndroidAPS-DexcomG6")
    DEXCOM_G6_NATIVE("AndroidAPS-DexcomG6"),
    @SerializedName("AndroidAPS-DexcomG5")
    DEXCOM_G5_NATIVE("AndroidAPS-DexcomG5"),
    @SerializedName("Bluetooth Wixel")
    DEXCOM_G4_WIXEL("Bluetooth Wixel"),
    @SerializedName("xBridge Wixel")
    DEXCOM_G4_XBRIDGE("xBridge Wixel"),
    @SerializedName("G4 Share Receiver")
    DEXCOM_G4_NATIVE("G4 Share Receiver"),
    @SerializedName("Medtrum A6")
    MEDTRUM_A6("Medtrum A6"),
    @SerializedName("Network G4")
    DEXCOM_G4_NET("Network G4"),
    @SerializedName("Network G4 and xBridge")
    DEXCOM_G4_NET_XBRIDGE("Network G4 and xBridge"),
    @SerializedName("Network G4 and Classic xDrip")
    DEXCOM_G4_NET_CLASSIC("Network G4 and Classic xDrip"),
    @SerializedName("DexcomG5")
    DEXCOM_G5_XDRIP("DexcomG5"),
    @SerializedName("G6 Native")
    DEXCOM_G6_NATIVE_XDRIP("G6 Native"),
    @SerializedName("G5 Native")
    DEXCOM_G5_NATIVE_XDRIP("G5 Native"),
    @SerializedName("G6 Native / G5 Native")
    DEXCOM_G6_G5_NATIVE_XDRIP("G6 Native / G5 Native"),
    @SerializedName("Network libre")
    LIBRE_1_NET("Network libre"),
    @SerializedName("BlueReader")
    LIBRE_1_BLUE("BlueReader"),
    @SerializedName("Transmiter PL")
    LIBRE_1_PL("Transmiter PL"),
    @SerializedName("Blucon")
    LIBRE_1_BLUCON("Blucon"),
    @SerializedName("Tomato")
    LIBRE_1_TOMATO("Tomato"),
    @SerializedName("Rfduino")
    LIBRE_1_RF("Rfduino"),
    @SerializedName("LimiTTer")
    LIBRE_1_LIMITTER("LimiTTer"),
    @SerializedName("Glimp")
    GLIMP("Glimp"),
    @SerializedName("Libre2")
    LIBRE_2_NATIVE("Libre2"),
    @SerializedName("Poctech")
    POCTECH_NATIVE("Poctech"),
    @SerializedName("MM600Series")
    MM_600_SERIES("MM600Series"),
    @SerializedName("Eversense")
    EVERSENSE("Eversense"),
    @SerializedName("Random")
    RANDOM("Random"),
    @SerializedName("Unknown")
    UNKNOWN("Unknown"),

    @SerializedName("IOBPrediction")
    IOB_PREDICTION("IOBPrediction"),
    @SerializedName("aCOBPrediction")
    aCOB_PREDICTION("aCOBPrediction"),
    @SerializedName("COBPrediction")
    COB_PREDICTION("COBPrediction"),
    @SerializedName("UAMPrediction")
    UAM_PREDICTION("UAMPrediction"),
    @SerializedName("ZTPrediction")
    ZT_PREDICTION("ZTPrediction")
}

fun GlucoseValue.SourceSensor.toDomain(): SourceSensorDomain = when(this) {
    GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN     -> SourceSensorDomain.DEXCOM_NATIVE_UNKNOWN
    GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE          -> SourceSensorDomain.DEXCOM_G6_NATIVE
    GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE          -> SourceSensorDomain.DEXCOM_G5_NATIVE
    GlucoseValue.SourceSensor.DEXCOM_G4_WIXEL           -> SourceSensorDomain.DEXCOM_G4_WIXEL
    GlucoseValue.SourceSensor.DEXCOM_G4_XBRIDGE         -> SourceSensorDomain.DEXCOM_G4_XBRIDGE
    GlucoseValue.SourceSensor.DEXCOM_G4_NATIVE          -> SourceSensorDomain.DEXCOM_G4_NATIVE
    GlucoseValue.SourceSensor.MEDTRUM_A6                -> SourceSensorDomain.MEDTRUM_A6
    GlucoseValue.SourceSensor.DEXCOM_G4_NET             -> SourceSensorDomain.DEXCOM_G4_NET
    GlucoseValue.SourceSensor.DEXCOM_G4_NET_XBRIDGE     -> SourceSensorDomain.DEXCOM_G4_NET_XBRIDGE
    GlucoseValue.SourceSensor.DEXCOM_G4_NET_CLASSIC     -> SourceSensorDomain.DEXCOM_G4_NET_CLASSIC
    GlucoseValue.SourceSensor.DEXCOM_G5_XDRIP           -> SourceSensorDomain.DEXCOM_G5_XDRIP
    GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP    -> SourceSensorDomain.DEXCOM_G6_NATIVE_XDRIP
    GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE_XDRIP    -> SourceSensorDomain.DEXCOM_G5_NATIVE_XDRIP
    GlucoseValue.SourceSensor.DEXCOM_G6_G5_NATIVE_XDRIP -> SourceSensorDomain.DEXCOM_G6_G5_NATIVE_XDRIP
    GlucoseValue.SourceSensor.LIBRE_1_NET               -> SourceSensorDomain.LIBRE_1_NET
    GlucoseValue.SourceSensor.LIBRE_1_BLUE              -> SourceSensorDomain.LIBRE_1_BLUE
    GlucoseValue.SourceSensor.LIBRE_1_PL                -> SourceSensorDomain.LIBRE_1_PL
    GlucoseValue.SourceSensor.LIBRE_1_BLUCON            -> SourceSensorDomain.LIBRE_1_BLUCON
    GlucoseValue.SourceSensor.LIBRE_1_TOMATO            -> SourceSensorDomain.LIBRE_1_TOMATO
    GlucoseValue.SourceSensor.LIBRE_1_RF                -> SourceSensorDomain.LIBRE_1_LIMITTER
    GlucoseValue.SourceSensor.LIBRE_1_LIMITTER          -> SourceSensorDomain.LIBRE_1_LIMITTER
    GlucoseValue.SourceSensor.GLIMP                     -> SourceSensorDomain.GLIMP
    GlucoseValue.SourceSensor.LIBRE_2_NATIVE            -> SourceSensorDomain.LIBRE_2_NATIVE
    GlucoseValue.SourceSensor.POCTECH_NATIVE            -> SourceSensorDomain.POCTECH_NATIVE
    GlucoseValue.SourceSensor.MM_600_SERIES             -> SourceSensorDomain.MM_600_SERIES
    GlucoseValue.SourceSensor.EVERSENSE                 -> SourceSensorDomain.EVERSENSE
    GlucoseValue.SourceSensor.RANDOM                    -> SourceSensorDomain.RANDOM
    GlucoseValue.SourceSensor.UNKNOWN                   -> SourceSensorDomain.UNKNOWN
    GlucoseValue.SourceSensor.IOB_PREDICTION            -> SourceSensorDomain.IOB_PREDICTION
    GlucoseValue.SourceSensor.aCOB_PREDICTION           -> SourceSensorDomain.aCOB_PREDICTION
    GlucoseValue.SourceSensor.COB_PREDICTION            -> SourceSensorDomain.COB_PREDICTION
    GlucoseValue.SourceSensor.UAM_PREDICTION            -> SourceSensorDomain.UAM_PREDICTION
    GlucoseValue.SourceSensor.ZT_PREDICTION             -> SourceSensorDomain.ZT_PREDICTION
}

fun SourceSensorDomain.toPersistence(): GlucoseValue.SourceSensor = when(this) {
    SourceSensorDomain.DEXCOM_NATIVE_UNKNOWN     -> GlucoseValue.SourceSensor.DEXCOM_NATIVE_UNKNOWN
    SourceSensorDomain.DEXCOM_G6_NATIVE          -> GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE
    SourceSensorDomain.DEXCOM_G5_NATIVE          -> GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE
    SourceSensorDomain.DEXCOM_G4_WIXEL           -> GlucoseValue.SourceSensor.DEXCOM_G4_WIXEL
    SourceSensorDomain.DEXCOM_G4_XBRIDGE         -> GlucoseValue.SourceSensor.DEXCOM_G4_XBRIDGE
    SourceSensorDomain.DEXCOM_G4_NATIVE          -> GlucoseValue.SourceSensor.DEXCOM_G4_NATIVE
    SourceSensorDomain.MEDTRUM_A6                -> GlucoseValue.SourceSensor.MEDTRUM_A6
    SourceSensorDomain.DEXCOM_G4_NET             -> GlucoseValue.SourceSensor.DEXCOM_G4_NET
    SourceSensorDomain.DEXCOM_G4_NET_XBRIDGE     -> GlucoseValue.SourceSensor.DEXCOM_G4_NET_XBRIDGE
    SourceSensorDomain.DEXCOM_G4_NET_CLASSIC     -> GlucoseValue.SourceSensor.DEXCOM_G4_NET_CLASSIC
    SourceSensorDomain.DEXCOM_G5_XDRIP           -> GlucoseValue.SourceSensor.DEXCOM_G5_XDRIP
    SourceSensorDomain.DEXCOM_G6_NATIVE_XDRIP    -> GlucoseValue.SourceSensor.DEXCOM_G6_NATIVE_XDRIP
    SourceSensorDomain.DEXCOM_G5_NATIVE_XDRIP    -> GlucoseValue.SourceSensor.DEXCOM_G5_NATIVE_XDRIP
    SourceSensorDomain.DEXCOM_G6_G5_NATIVE_XDRIP -> GlucoseValue.SourceSensor.DEXCOM_G6_G5_NATIVE_XDRIP
    SourceSensorDomain.LIBRE_1_NET               -> GlucoseValue.SourceSensor.LIBRE_1_NET
    SourceSensorDomain.LIBRE_1_BLUE              -> GlucoseValue.SourceSensor.LIBRE_1_BLUE
    SourceSensorDomain.LIBRE_1_PL                -> GlucoseValue.SourceSensor.LIBRE_1_PL
    SourceSensorDomain.LIBRE_1_BLUCON            -> GlucoseValue.SourceSensor.LIBRE_1_BLUCON
    SourceSensorDomain.LIBRE_1_TOMATO            -> GlucoseValue.SourceSensor.LIBRE_1_TOMATO
    SourceSensorDomain.LIBRE_1_RF                -> GlucoseValue.SourceSensor.LIBRE_1_LIMITTER
    SourceSensorDomain.LIBRE_1_LIMITTER          -> GlucoseValue.SourceSensor.LIBRE_1_LIMITTER
    SourceSensorDomain.GLIMP                     -> GlucoseValue.SourceSensor.GLIMP
    SourceSensorDomain.LIBRE_2_NATIVE            -> GlucoseValue.SourceSensor.LIBRE_2_NATIVE
    SourceSensorDomain.POCTECH_NATIVE            -> GlucoseValue.SourceSensor.POCTECH_NATIVE
    SourceSensorDomain.MM_600_SERIES             -> GlucoseValue.SourceSensor.MM_600_SERIES
    SourceSensorDomain.EVERSENSE                 -> GlucoseValue.SourceSensor.EVERSENSE
    SourceSensorDomain.RANDOM                    -> GlucoseValue.SourceSensor.RANDOM
    SourceSensorDomain.UNKNOWN                   -> GlucoseValue.SourceSensor.UNKNOWN
    SourceSensorDomain.IOB_PREDICTION            -> GlucoseValue.SourceSensor.IOB_PREDICTION
    SourceSensorDomain.aCOB_PREDICTION           -> GlucoseValue.SourceSensor.aCOB_PREDICTION
    SourceSensorDomain.COB_PREDICTION            -> GlucoseValue.SourceSensor.COB_PREDICTION
    SourceSensorDomain.UAM_PREDICTION            -> GlucoseValue.SourceSensor.UAM_PREDICTION
    SourceSensorDomain.ZT_PREDICTION             -> GlucoseValue.SourceSensor.ZT_PREDICTION
}