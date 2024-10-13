package app.aaps.core.data.model

enum class SourceSensor(val text: String) {
    DEXCOM_NATIVE_UNKNOWN("AAPS-Dexcom"),
    DEXCOM_G6_NATIVE("AAPS-DexcomG6"),
    DEXCOM_G5_NATIVE("AAPS-DexcomG5"),
    DEXCOM_G4_WIXEL("Bluetooth Wixel"),
    DEXCOM_G4_XBRIDGE("xBridge Wixel"),
    DEXCOM_G4_NATIVE("G4 Share Receiver"),
    MEDTRUM_A6("Medtrum A6"),
    DEXCOM_G4_NET("Network G4"),
    DEXCOM_G4_NET_XBRIDGE("Network G4 and xBridge"),
    DEXCOM_G4_NET_CLASSIC("Network G4 and Classic xDrip"),
    DEXCOM_G5_XDRIP("DexcomG5"),
    DEXCOM_G6_NATIVE_XDRIP("G6 Native"),
    DEXCOM_G5_NATIVE_XDRIP("G5 Native"),
    DEXCOM_G7_NATIVE_XDRIP("G7 Native"),
    LIBRE_1_OTHER("Other App"),
    LIBRE_1_NET("Network libre"),
    LIBRE_1_BLUE("BlueReader"),
    LIBRE_1_PL("Transmiter PL"),
    LIBRE_1_BLUCON("Blucon"),
    LIBRE_1_TOMATO("Tomato"),
    LIBRE_1_RF("Rfduino"),
    LIBRE_1_LIMITTER("LimiTTer"),
    LIBRE_1_BUBBLE("Bubble"),
    LIBRE_1_ATOM("Bubble"),
    LIBRE_1_GLIMP("Glimp"),
    LIBRE_2_NATIVE("Libre2"),
    POCTECH_NATIVE("Poctech"),
    GLUNOVO_NATIVE("Glunovo"),
    INTELLIGO_NATIVE("Intelligo"),
    MM_600_SERIES("MM600Series"),
    OTTAI("Ottai"),
    EVERSENSE("Eversense"),
    AIDEX("GlucoRx Aidex"),
    SYAI_TAG("Syai Tag"),
    RANDOM("Random"),
    UNKNOWN("Unknown"),

    IOB_PREDICTION("IOBPrediction"),
    A_COB_PREDICTION("aCOBPrediction"),
    COB_PREDICTION("COBPrediction"),
    UAM_PREDICTION("UAMPrediction"),
    ZT_PREDICTION("ZTPrediction"),
    ;

    fun isLibre(): Boolean = arrayListOf(
        LIBRE_1_OTHER,
        LIBRE_1_NET,
        LIBRE_1_BLUE,
        LIBRE_1_PL,
        LIBRE_1_BLUCON,
        LIBRE_1_TOMATO,
        LIBRE_1_RF,
        LIBRE_1_LIMITTER,
        LIBRE_1_BUBBLE,
        LIBRE_1_ATOM,
        LIBRE_1_GLIMP,
        LIBRE_2_NATIVE,
        UNKNOWN // Better check for FLAT on unknown sources too
    ).any { it.text == text }

    companion object {

        fun fromString(source: String?) = entries.firstOrNull { it.text == source } ?: UNKNOWN

    }
}
