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
 * Which way the step between the two ISF values on the chip goes.
 *
 * Worked out where the numbers still are numbers. Reading the direction back out of the formatted
 * text cannot be done: the digits and the decimal separator come from the platform locale, so a
 * language that writes neither ASCII digits nor a dot or comma does not parse back.
 *
 * [SAME] is the right answer whenever the two texts read alike, even if the numbers behind them
 * differ a little. The chip then shows the same number twice and an arrow would contradict it.
 */
enum class IsfStep { UP, DOWN, SAME }

/**
 * @param asText the autosens percentage for the chip, empty when it is exactly 100 or unknown
 * @param isfFrom the profile ISF for the chip, empty when the algorithm uses it unchanged
 * @param isfTo the ISF in use for the chip, empty when the algorithm uses the profile ISF unchanged
 * @param isfStep which way the step from [isfFrom] to [isfTo] goes
 * @param lines one translated line per value, in reading order; empty when nothing is known
 * @param ratio the last autosens ratio, 1.0 when unknown
 * @param isEnabled whether autosens is switched on, on this phone or, for a client, on the master
 * @param hasData whether a sensitivity result exists at all
 */
data class SensitivityOverviewData(
    val asText: String = "",
    val isfFrom: String = "",
    val isfTo: String = "",
    val isfStep: IsfStep = IsfStep.SAME,
    val lines: List<String> = emptyList(),
    val ratio: Double = 1.0,
    val isEnabled: Boolean = true,
    val hasData: Boolean = false
)
