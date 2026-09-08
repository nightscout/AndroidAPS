package app.aaps.plugins.constraints.objectives.objectives

/**
 * Renders a duration as "3 d", "5 h" or "20 min".
 *
 * The one part of [Objective] that could not move to shared code. Android says this with **plural
 * resources** - `R.plurals.days` and friends - because "1 day" and "2 days" differ per language, and
 * some languages have more than two forms. `TextRef` has no plural form, so there is nothing in the
 * shared string machinery to express it with.
 *
 * Rather than flatten the Android text to something a translator cannot fix, the choice is lifted
 * out: Android keeps its plurals, and the platforms without a plural table say it plainly.
 *
 * If plurals ever land in `TextRef`, this interface is what should disappear.
 */
interface DurationText {

    /** [durationMs] as a short, human duration. Coarse on purpose - the largest unit that fits. */
    fun format(durationMs: Long): String
}
