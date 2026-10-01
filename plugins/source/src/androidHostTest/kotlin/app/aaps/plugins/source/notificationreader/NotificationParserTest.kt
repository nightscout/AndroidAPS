package app.aaps.plugins.source.notificationreader

import app.aaps.core.data.model.SourceSensor
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NotificationParserTest {

    private lateinit var parser: NotificationParser

    companion object {

        val TEST_CONFIG = PackageConfig(
            version = 1,
            supportedPackages = setOf(
                "com.dexcom.g7", "com.dexcom.g6", "com.dexcom.g6.region1.mmol",
                "com.dexcom.dexcomone", "com.dexcom.stelo", "com.camdiab.fx_alert.mmoll",
                "com.medtronic.diabetes.guardian", "com.medtronic.diabetes.minimedmobile.eu",
                "com.senseonics.gen12androidapp", "com.microtech.aidexx",
                "com.ottai.seas", "com.sinocare.cgm.ce",
            ),
            packageToSensor = mapOf(
                "com.dexcom.g7" to SourceSensor.DEXCOM_G7_NATIVE,
                "com.dexcom.g6" to SourceSensor.DEXCOM_G6_NATIVE,
                "com.dexcom.g6.region1.mmol" to SourceSensor.DEXCOM_G6_NATIVE,
                "com.dexcom.dexcomone" to SourceSensor.DEXCOM_NATIVE_UNKNOWN,
                "com.dexcom.stelo" to SourceSensor.DEXCOM_NATIVE_UNKNOWN,
                "com.camdiab.fx_alert.mmoll" to SourceSensor.DEXCOM_NATIVE_UNKNOWN,
                "com.medtronic.diabetes.guardian" to SourceSensor.MM_600_SERIES,
                "com.medtronic.diabetes.minimedmobile.eu" to SourceSensor.MM_600_SERIES,
                "com.senseonics.gen12androidapp" to SourceSensor.EVERSENSE,
                "com.microtech.aidexx" to SourceSensor.AIDEX,
                "com.ottai.seas" to SourceSensor.OTTAI,
                "com.sinocare.cgm.ce" to SourceSensor.SINO,
            )
        )
    }

    @BeforeEach
    fun setup() {
        parser = NotificationParser(TEST_CONFIG)
    }

    @Nested
    inner class CleanTextTest {

        @Test
        fun `remove mg-dL label`() {
            assertThat(parser.cleanText("142 mg/dL")).isEqualTo("142")
        }

        @Test
        fun `remove mg-dL label case insensitive`() {
            assertThat(parser.cleanText("142 mg/dl")).isEqualTo("142")
        }

        @Test
        fun `remove mmol-L label`() {
            assertThat(parser.cleanText("7.9 mmol/L")).isEqualTo("7.9")
        }

        @Test
        fun `remove mmol-L label case insensitive`() {
            assertThat(parser.cleanText("7.9 mmol/l")).isEqualTo("7.9")
        }

        @Test
        fun `remove unicode arrows`() {
            assertThat(parser.cleanText("142 \u2197")).isEqualTo("142")
        }

        @Test
        fun `remove multiple arrow types`() {
            // U+2191 (Arrows block) and U+2B06 (Misc Symbols block)
            assertThat(parser.cleanText("\u2191 142 \u2B06")).isEqualTo("142")
        }

        @Test
        fun `remove non-breaking space`() {
            assertThat(parser.cleanText("142\u00a0mg/dL")).isEqualTo("142")
        }

        @Test
        fun `remove word joiner`() {
            assertThat(parser.cleanText("142\u2060")).isEqualTo("142")
        }

        @Test
        fun `remove inequality signs`() {
            assertThat(parser.cleanText("≤40")).isEqualTo("40")
            assertThat(parser.cleanText("≥400")).isEqualTo("400")
        }

        @Test
        fun `plain number unchanged`() {
            assertThat(parser.cleanText("142")).isEqualTo("142")
        }

        @Test
        fun `mmol value with comma unchanged`() {
            assertThat(parser.cleanText("7,9")).isEqualTo("7,9")
        }

        @Test
        fun `dingbats removed`() {
            // U+2764 is in Dingbats range
            assertThat(parser.cleanText("142\u2764")).isEqualTo("142")
        }
    }

    @Nested
    inner class ParseGlucoseMgdlTest {

        @Test
        fun `parse mg-dL integer`() {
            assertThat(parser.parseGlucoseMgdl("142", useMgdl = true)).isEqualTo(142)
        }

        @Test
        fun `parse mg-dL with unit label`() {
            assertThat(parser.parseGlucoseMgdl("142 mg/dL", useMgdl = true)).isEqualTo(142)
        }

        @Test
        fun `parse mg-dL with arrow`() {
            assertThat(parser.parseGlucoseMgdl("142 \u2197", useMgdl = true)).isEqualTo(142)
        }

        @Test
        fun `parse mmol-L with dot`() {
            val mgdl = parser.parseGlucoseMgdl("7.9", useMgdl = false)
            assertThat(mgdl).isNotNull()
            assertThat(mgdl!!).isIn(142..143)
        }

        @Test
        fun `parse mmol-L with comma`() {
            val mgdl = parser.parseGlucoseMgdl("7,9", useMgdl = false)
            assertThat(mgdl).isNotNull()
            assertThat(mgdl!!).isIn(142..143)
        }

        @Test
        fun `parse mmol-L with unit label`() {
            val mgdl = parser.parseGlucoseMgdl("7.9 mmol/L", useMgdl = false)
            assertThat(mgdl).isNotNull()
            assertThat(mgdl!!).isIn(142..143)
        }

        @Test
        fun `reject non-numeric text`() {
            assertThat(parser.parseGlucoseMgdl("High", useMgdl = true)).isNull()
        }

        @Test
        fun `reject integer in mmol mode`() {
            assertThat(parser.parseGlucoseMgdl("142", useMgdl = false)).isNull()
        }

        @Test
        fun `reject blank text`() {
            assertThat(parser.parseGlucoseMgdl("   ", useMgdl = true)).isNull()
        }

        @Test
        fun `reject empty text`() {
            assertThat(parser.parseGlucoseMgdl("", useMgdl = true)).isNull()
        }
    }

    @Nested
    inner class ExtractGlucoseTest {

        @Test
        fun `single valid text returns result`() {
            val result = parser.extractGlucose(listOf("142"), "com.dexcom.g7", useMgdl = true)
            assertThat(result).isNotNull()
            assertThat(result!!.glucoseMgdl).isEqualTo(142)
            assertThat(result.sourceSensor).isEqualTo(SourceSensor.DEXCOM_G7_NATIVE)
        }

        @Test
        fun `one valid among non-numeric texts`() {
            val result = parser.extractGlucose(listOf("Dexcom G7", "142", "5 min ago"), "com.dexcom.g7", useMgdl = true)
            assertThat(result).isNotNull()
            assertThat(result!!.glucoseMgdl).isEqualTo(142)
        }

        @Test
        fun `two valid numbers returns null (ambiguous)`() {
            assertThat(parser.extractGlucose(listOf("142", "85"), "com.dexcom.g7", useMgdl = true)).isNull()
        }

        @Test
        fun `no valid text returns null`() {
            assertThat(parser.extractGlucose(listOf("Dexcom G7", "5 min ago"), "com.dexcom.g7", useMgdl = true)).isNull()
        }

        @Test
        fun `empty list returns null`() {
            assertThat(parser.extractGlucose(emptyList(), "com.dexcom.g7", useMgdl = true)).isNull()
        }

        @Test
        fun `value below range rejected`() {
            assertThat(parser.extractGlucose(listOf("30"), "com.dexcom.g7", useMgdl = true)).isNull()
        }

        @Test
        fun `value above range rejected`() {
            assertThat(parser.extractGlucose(listOf("500"), "com.dexcom.g7", useMgdl = true)).isNull()
        }

        @Test
        fun `value at lower boundary accepted`() {
            val result = parser.extractGlucose(listOf("40"), "com.dexcom.g7", useMgdl = true)
            assertThat(result).isNotNull()
            assertThat(result!!.glucoseMgdl).isEqualTo(40)
        }

        @Test
        fun `value at upper boundary accepted`() {
            val result = parser.extractGlucose(listOf("405"), "com.dexcom.g7", useMgdl = true)
            assertThat(result).isNotNull()
            assertThat(result!!.glucoseMgdl).isEqualTo(405)
        }

        @Test
        fun `mmol-L from medtronic`() {
            val result = parser.extractGlucose(listOf("8.3 mmol/L"), "com.medtronic.diabetes.guardian", useMgdl = false)
            assertThat(result).isNotNull()
            assertThat(result!!.glucoseMgdl).isIn(149..150)
            assertThat(result.sourceSensor).isEqualTo(SourceSensor.MM_600_SERIES)
        }

        @Test
        fun `unknown package returns UNKNOWN sensor`() {
            val result = parser.extractGlucose(listOf("142"), "com.unknown.app", useMgdl = true)
            assertThat(result).isNotNull()
            assertThat(result!!.sourceSensor).isEqualTo(SourceSensor.UNKNOWN)
        }
    }

    @Nested
    inner class ParseMmolAsMgdlTest {

        @Test
        fun `valid mmol with dot`() {
            val result = NotificationParser.parseMmolAsMgdl("7.9")
            assertThat(result).isNotNull()
            assertThat(result!!).isIn(142..143)
        }

        @Test
        fun `valid mmol with comma`() {
            val result = NotificationParser.parseMmolAsMgdl("7,9")
            assertThat(result).isNotNull()
            assertThat(result!!).isIn(142..143)
        }

        @Test
        fun `integer rejected`() {
            assertThat(NotificationParser.parseMmolAsMgdl("142")).isNull()
        }

        @Test
        fun `text rejected`() {
            assertThat(NotificationParser.parseMmolAsMgdl("abc")).isNull()
        }

        @Test
        fun `high precision`() {
            val result = NotificationParser.parseMmolAsMgdl("12.34")
            assertThat(result).isNotNull()
            assertThat(result!!).isIn(222..223)
        }
    }
}
