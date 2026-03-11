package app.aaps.core.data.model

enum class SourceSensor(val text: String) {
    DEXCOM_NATIVE_UNKNOWN("AAPS-Dexcom"),
    DEXCOM_G6_NATIVE("AAPS-DexcomG6"),
    DEXCOM_G7_NATIVE("AAPS-DexcomG7"),
    MEDTRUM_A6("Medtrum A6"),
    DEXCOM_G6_NATIVE_XDRIP("G6 Native"),
    DEXCOM_G7_NATIVE_XDRIP("G7 Native"),
    DEXCOM_G7_XDRIP("G7"),
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
    LIBRE_2("Libre2"),
    LIBRE_2_NATIVE("Libre2 Native"),
    LIBRE_3("Libre3"),
    POCTECH_NATIVE("Poctech"),
    GLUNOVO_NATIVE("Glunovo"),
    INTELLIGO_NATIVE("Intelligo"),
    MM_600_SERIES("MM600Series"),
    OTTAI("Ottai"),
    SIBIONIC("SI App"),
    SINO("Sino App"),
    EVERSENSE_E3("Eversense E3"),
    EVERSENSE_365("Eversense 365"),
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

    fun isLibre1(): Boolean = arrayListOf(
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
        UNKNOWN // Better check for FLAT on unknown sources too
    ).any { it.text == text }

    companion object {

        fun fromString(source: String?) = entries.firstOrNull { it.text == source } ?: UNKNOWN

    }
}
