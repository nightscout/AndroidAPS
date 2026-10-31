package app.aaps.core.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * In `commonTest`, so this runs through Kotlin/Native as well as the JVM. `kotlin.test` rather than
 * Truth and JUnit 5, because neither of those exists off the JVM.
 */
class SourceSensorTest {

    @Test
    fun `canonical names resolve`() {
        assertEquals(SourceSensor.DEXCOM_G6, SourceSensor.fromString("Dexcom G6"))
        assertEquals(SourceSensor.DEXCOM_G7, SourceSensor.fromString("Dexcom G7"))
        assertEquals(SourceSensor.DEXCOM_UNKNOWN, SourceSensor.fromString("Dexcom"))
        assertEquals(SourceSensor.LIBRE_1, SourceSensor.fromString("Libre1"))
        assertEquals(SourceSensor.LIBRE_2, SourceSensor.fromString("Libre2"))
        assertEquals(SourceSensor.LIBRE_3, SourceSensor.fromString("Libre3"))
        assertEquals(SourceSensor.MEDTRUM_UNKNOWN, SourceSensor.fromString("Medtrum"))
        assertEquals(SourceSensor.MM_UNKNOWN, SourceSensor.fromString("Medtronic"))
    }

    @Test
    fun `older aaps names still resolve`() {
        assertEquals(SourceSensor.DEXCOM_UNKNOWN, SourceSensor.fromString("AAPS-Dexcom"))
        assertEquals(SourceSensor.DEXCOM_G6, SourceSensor.fromString("AAPS-DexcomG6"))
        assertEquals(SourceSensor.DEXCOM_G7, SourceSensor.fromString("AAPS-DexcomG7"))
        assertEquals(SourceSensor.DEXCOM_UNKNOWN, SourceSensor.fromString("Share Follow"))
        assertEquals(SourceSensor.MM_UNKNOWN, SourceSensor.fromString("CareLink Follow"))
        assertEquals(SourceSensor.MEDTRUM_UNKNOWN, SourceSensor.fromString("Medtrum Native"))
        assertEquals(SourceSensor.LIBRE_2, SourceSensor.fromString("Libre2 Native"))
    }

    @Test
    fun `names sent by xdrip resolve`() {
        assertEquals(SourceSensor.DEXCOM_G6, SourceSensor.fromString("G6 Native"))
        assertEquals(SourceSensor.DEXCOM_G7, SourceSensor.fromString("G7 Native"))
        assertEquals(SourceSensor.DEXCOM_G7, SourceSensor.fromString("G7"))
        assertEquals(SourceSensor.GLUPRO, SourceSensor.fromString("GluPro"))
    }

    @Test
    fun `names sent by juggluco resolve`() {
        assertEquals(SourceSensor.LIBRE_2, SourceSensor.fromString("Libre2"))
        assertEquals(SourceSensor.LIBRE_3, SourceSensor.fromString("Libre3"))
        assertEquals(SourceSensor.DEXCOM_G7, SourceSensor.fromString("G7"))
        assertEquals(SourceSensor.SIBIONIC_GS1, SourceSensor.fromString("GS1Sb"))
        assertEquals(SourceSensor.SIBIONIC_GS3, SourceSensor.fromString("GS3"))
        assertEquals(SourceSensor.ACCU_CHEK, SourceSensor.fromString("AccuChek"))
        assertEquals(SourceSensor.CARESENS_AIR, SourceSensor.fromString("CareSenseAir"))
        assertEquals(SourceSensor.AIDEX_X, SourceSensor.fromString("AidexX"))
    }

    /**
     * Labels from JugglucoNG pull request 578, which names each sensor instead of claiming Libre 2
     * for all of them. They arrive only once that is released, and until then they simply never
     * match.
     */
    @Test
    fun `names added by juggluco ng resolve`() {
        // Ottai is the other brand name of the Syai sensor, so it is an alias and not an entry.
        assertEquals(SourceSensor.SYAI_TAG, SourceSensor.fromString("Ottai"))
        assertEquals(SourceSensor.SYAI_TAG, SourceSensor.fromString("Syai Tag"))
        assertEquals(SourceSensor.SINOCARE, SourceSensor.fromString("iCan"))
        // The same Sinocare sensor, read by an older version of its app.
        assertEquals(SourceSensor.SINOCARE, SourceSensor.fromString("Sino App"))
        assertEquals(SourceSensor.ANYTIME, SourceSensor.fromString("Anytime"))
        assertEquals(SourceSensor.GLUTEC, SourceSensor.fromString("MQ"))
        // Its API source relays another app's readings and reports this.
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString("Unknown"))
        // Its Nightscout follower names the platform, not the sensor, so it stays unknown.
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString("Nightscout"))
    }

    @Test
    fun `every libre 1 bridge resolves to the one libre 1 entry`() {
        listOf("Other App", "Network libre", "BlueReader", "Transmiter PL", "Blucon", "Tomato", "Rfduino", "LimiTTer", "Bubble", "Atom", "Glimp")
            .forEach { assertEquals(SourceSensor.LIBRE_1, SourceSensor.fromString(it), it) }
    }

    @Test
    fun `only the first part before the separator is matched`() {
        assertEquals(SourceSensor.DEXCOM_G6, SourceSensor.fromString("G6 Native::Backfill"))
        assertEquals(SourceSensor.DEXCOM_G7, SourceSensor.fromString("G7::Insufficient"))
        assertEquals(SourceSensor.DEXCOM_G6, SourceSensor.fromString("G6 Native::Backfill::BlueJay"))
    }

    @Test
    fun `case is ignored so both xdrip spellings of blucon resolve`() {
        assertEquals(SourceSensor.LIBRE_1, SourceSensor.fromString("Blucon"))
        assertEquals(SourceSensor.LIBRE_1, SourceSensor.fromString("BluCon"))
    }

    @Test
    fun `followers that do not name their sensor stay unknown`() {
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString("Nightscout Follow"))
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString("NSClient Follow"))
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString("NSEmulator Follow"))
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString("G5 Native"))
    }

    @Test
    fun `missing and empty values are unknown`() {
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString(null))
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString(""))
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString("Something we never heard of"))
    }

    @Test
    fun `a sensor name is never matched inside an unrelated text`() {
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString("Some other app G7"))
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString("Libre2 extra"))
        // The xDrip prefixed form needs XdripSourceResolver, not this.
        assertEquals(SourceSensor.UNKNOWN, SourceSensor.fromString("xDrip-Medtrum"))
    }

    @Test
    fun `every name is unique when case is ignored`() {
        val names = SourceSensor.entries.flatMap { entry -> entry.aliases + entry.text }.map { it.lowercase() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every name resolves back to its own entry`() {
        SourceSensor.entries.forEach { entry ->
            (entry.aliases + entry.text).forEach { name ->
                assertEquals(entry, SourceSensor.fromString(name), name)
            }
        }
    }
}
