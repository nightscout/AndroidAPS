package app.aaps.plugins.source.notificationreader

import app.aaps.core.data.model.SourceSensor
import kotlin.math.roundToInt

/**
 * Parses glucose values from CGM app notification text.
 * Pure Kotlin (no Android dependencies) for testability.
 */
class NotificationParser(private val packageConfig: PackageConfig) {

    data class GlucoseResult(
        val glucoseMgdl: Int,
        val sourceSensor: SourceSensor
    )

    /**
     * Extract a glucose value from notification text strings.
     * Returns result only if exactly one text yields a valid glucose (avoids ambiguity).
     */
    fun extractGlucose(texts: List<String>, packageName: String, useMgdl: Boolean): GlucoseResult? {
        val validValues = texts.mapNotNull { parseGlucoseMgdl(it, useMgdl) }
            .filter { it in GLUCOSE_RANGE }

        return if (validValues.size == 1)
            GlucoseResult(validValues.single(), packageConfig.sensorForPackage(packageName))
        else
            null
    }

    /**
     * Parse a glucose value from a single text string. Returns mg/dL or null.
     */
    fun parseGlucoseMgdl(text: String, useMgdl: Boolean): Int? {
        val cleaned = cleanText(text)
        if (cleaned.isBlank()) return null

        return if (useMgdl)
            cleaned.toIntOrNull()
        else
            parseMmolAsMgdl(cleaned)
    }

    /**
     * Strip unit labels, unicode arrows/symbols, and whitespace artifacts from notification text.
     */
    fun cleanText(value: String): String = value
        .stripUnicodeSymbols()
        .stripUnitLabels()
        .stripSpecialChars()
        .trim()

    companion object {

        val GLUCOSE_RANGE = 40..405
        private const val MMOLL_TO_MGDL = 18.0182

        private val MMOL_PATTERN = Regex("^\\d+[.,]\\d+$")

        // Unicode ranges containing arrow and symbol characters used by CGM apps
        private val SYMBOL_RANGES = listOf(
            '\u2190'..'\u21FF', // Arrows
            '\u2700'..'\u27BF', // Dingbats
            '\u2900'..'\u297F', // Supplemental Arrows-B
            '\u2B00'..'\u2BFF', // Misc Symbols and Arrows
        )

        private val UNIT_LABELS = Regex("m(?:g/d|mol/)l", RegexOption.IGNORE_CASE)

        fun parseMmolAsMgdl(text: String): Int? {
            if (!MMOL_PATTERN.matches(text)) return null
            val mmol = text.replace(',', '.').toDoubleOrNull() ?: return null
            return (mmol * MMOLL_TO_MGDL).roundToInt()
        }

        private fun String.stripUnicodeSymbols(): String = buildString(length) {
            for (c in this@stripUnicodeSymbols) {
                if (SYMBOL_RANGES.none { c in it }) append(c)
            }
        }

        private fun String.stripUnitLabels(): String = replace(UNIT_LABELS, "")

        private fun String.stripSpecialChars(): String = this
            .replace('\u00a0', ' ')  // non-breaking space → space
            .replace("\u2060", "")   // word joiner
            .replace("\\", "/")
            .replace("≤", "")
            .replace("≥", "")
    }
}
