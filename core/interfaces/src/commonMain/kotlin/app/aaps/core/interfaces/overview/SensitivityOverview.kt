package app.aaps.core.interfaces.overview

/**
 * The sensitivity as the user sees it: the autosens ratio and the ISF values in force, worked
 * out once for every place that shows them - the Overview chip and its dialog on the phone, the
 * Loop Status card on the watch. The lines are finished, translated text, so a reader adds nothing
 * but a title.
 */
interface SensitivityOverview {

    suspend fun build(): SensitivityOverviewData
}

/**
 * @param asText the autosens percentage for the chip, empty when it is exactly 100 or unknown
 * @param isfFrom the profile ISF for the chip, empty unless a variable ISF is in use
 * @param isfTo the ISF in use for the chip, empty unless a variable ISF is in use
 * @param lines one translated line per value, in reading order; empty when nothing is known
 * @param ratio the last autosens ratio, 1.0 when unknown
 * @param isEnabled whether autosens is switched on, on this phone or, for a client, on the master
 * @param hasData whether a sensitivity result exists at all
 */
data class SensitivityOverviewData(
    val asText: String = "",
    val isfFrom: String = "",
    val isfTo: String = "",
    val lines: List<String> = emptyList(),
    val ratio: Double = 1.0,
    val isEnabled: Boolean = true,
    val hasData: Boolean = false
)
