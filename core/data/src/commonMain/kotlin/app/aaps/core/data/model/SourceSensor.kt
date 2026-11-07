package app.aaps.core.data.model

/**
 * The sensor a reading came from.
 *
 * One entry per sensor, not per app or bridge that delivered it. Two entries exist only where the
 * hardware really differs and the data tells us which, so `AAPS-DexcomG6` and `G6 Native` are one
 * entry (same sensor, different route), while `GS1Sb` and `GS3` are two (different sensors). Where
 * a vendor is known but the model is not, the entry is named `<vendor>_UNKNOWN`.
 *
 * [text] is the one spelling AAPS writes out, and is what goes into the Nightscout `device` field.
 * [aliases] are read only: they hold the older and foreign spellings we still have to understand, so
 * a new sender spelling is a new alias and not a new entry.
 */
enum class SourceSensor(val text: String, val aliases: Set<String> = emptySet()) {
    DEXCOM_UNKNOWN("Dexcom", setOf("AAPS-Dexcom", "Share Follow")),
    DEXCOM_G6("Dexcom G6", setOf("AAPS-DexcomG6", "G6 Native")),
    DEXCOM_G7("Dexcom G7", setOf("AAPS-DexcomG7", "G7 Native", "G7")),
    LIBRE_1(
        "Libre1",
        setOf("Other App", "Network libre", "BlueReader", "Transmiter PL", "Blucon", "Tomato", "Rfduino", "LimiTTer", "Bubble", "Atom", "Glimp")
    ),
    LIBRE_2("Libre2", setOf("Libre2 Native")),
    LIBRE_3("Libre3"),
    MEDTRUM_A6("Medtrum A6"),
    MEDTRUM_UNKNOWN("Medtrum", setOf("Medtrum Native")),
    MM_600_SERIES("MM600Series"),
    MM_SIMPLERA("Simplera"),
    MM_UNKNOWN("Medtronic", setOf("CareLink Follow")),
    SIBIONIC_UNKNOWN("SI App"),
    SIBIONIC_GS1("GS1Sb"),
    SIBIONIC_GS3("GS3"),
    ACCU_CHEK("AccuChek"),
    CARESENS_AIR("CareSenseAir"),
    AIDEX("GlucoRx Aidex"),
    AIDEX_X("AidexX"),
    POCTECH_NATIVE("Poctech"),
    GLUNOVO_NATIVE("Glunovo"),
    INTELLIGO_NATIVE("Intelligo"),
    // Sinocare, whose CGM is sold as the iCan i3. Named after the vendor and not the product,
    // because neither label says which model it is: Juggluco sends `iCan` and names its sensors
    // `ICN-`, while `Sino App` is the same sensor read by an older version of the Sinocare app,
    // which the notification reader knows as the package `com.sinocare.cgm.ce`.
    SINOCARE("Sinocare", setOf("iCan", "Sino App")),
    // Yuwell Anytime, a family rather than one sensor: CT2.5, CT3 and its variants, CT4 and CT5.
    // The same hardware is also sold under other names. Juggluco sends one label for all of them.
    ANYTIME("Anytime"),
    // Glutec CGM. `MQ` is the name Juggluco sends, after the `MQ-` prefix of the sensor itself, so
    // it is kept as an alias while we write out the vendor name.
    GLUTEC("Glutec", setOf("MQ")),
    GLUPRO("GluPro"),
    EVERSENSE("Eversense"),
    // One sensor sold under two brands. AAPS already treats them as one: the broadcasts from both
    // apps go to the same worker, which has always stored this entry, and the plugin calls itself
    // "Syai/Ottai App". `Ottai` is only ever read, and is what Juggluco sends.
    SYAI_TAG("Syai Tag", setOf("Ottai")),
    INSTARA("Instara"),
    RANDOM("Random"),
    UNKNOWN("Unknown"),

    IOB_PREDICTION("IOBPrediction"),
    A_COB_PREDICTION("aCOBPrediction"),
    COB_PREDICTION("COBPrediction"),
    UAM_PREDICTION("UAMPrediction"),
    ZT_PREDICTION("ZTPrediction"),
    ;

    companion object {

        /**
         * Resolves a source name to its sensor, matching [text] and [aliases] exactly so that an
         * unrecognised name can never be mistaken for a sensor whose name it happens to contain.
         *
         * A sender may append parts with `::` (xDrip's `BgReading.appendSourceInfo` does, so a reading
         * can arrive as `G6 Native::Backfill`), and only the first part names the device. Case is
         * ignored, because senders spell the same device differently - xDrip sends `BluCon` or
         * `Blucon` depending on the phone language.
         *
         * A Nightscout `device` value written by xDrip carries a prefix and needs
         * `XdripSourceResolver.fromNightscoutDevice` instead, which falls back to this.
         *
         * Some sources are left out of this enum on purpose, so that they land on [UNKNOWN] and keep
         * the flat check that [needsFlatBgCheck] gives an unknown source. Do not add entries or
         * aliases for them:
         *  - `Nightscout Follow`, `NSClient Follow` and `NSEmulator Follow` from xDrip, and
         *    `Nightscout` from Juggluco, relay readings from any app, so the real sensor is not known.
         *  - `G5 Native` is sent for a G5, but also for an xDrip WebFollow reading, so it does not
         *    identify the hardware either.
         */
        fun fromString(source: String?): SourceSensor {
            val wanted = source?.substringBefore("::")?.trim()?.takeIf { it.isNotEmpty() } ?: return UNKNOWN
            return entries.firstOrNull { entry ->
                entry.text.equals(wanted, ignoreCase = true) || entry.aliases.any { it.equals(wanted, ignoreCase = true) }
            } ?: UNKNOWN
        }
    }
}
