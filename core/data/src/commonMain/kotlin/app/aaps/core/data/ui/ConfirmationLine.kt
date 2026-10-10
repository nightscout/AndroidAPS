package app.aaps.core.data.ui

/**
 * Semantic role of a confirmation-summary line. The UI maps it to a theme color at render time — a
 * [ConfirmationLine] never carries a [androidx.compose.ui.graphics.Color], so it can be produced by
 * non-Composable domain code (e.g. the bolus wizard) and themed where it's drawn.
 *
 * [NORMAL] is untinted (plain text); [PRIMARY] takes the host dialog's element color (so a temp-basal
 * value reads in the temp-basal color, etc.); the rest map to fixed semantic theme colors.
 */
enum class ConfirmationRole {

    NORMAL, PRIMARY, BOLUS, CARBS, COB, WARNING, INFO, TEMP_TARGET, SCENE,
    LOOP_CLOSED, LOOP_OPEN, LOOP_LGS, LOOP_SUSPENDED, LOOP_DISABLED, LOOP_DISCONNECTED
}

/**
 * One line of an action-confirmation summary: a [role] (mapped to a theme color at render time) plus a
 * **complete, already-localized** [text]. The caller must assemble [text] from string-resource templates
 * (e.g. `format_insulin_units`, `format_carbs`, `confirmation_line` for `label: value`) — never by joining
 * label/value/unit in code, which breaks RTL and translation. The renderer tints the whole line by [role].
 */
data class ConfirmationLine(val role: ConfirmationRole, val text: String)

/** Builder collected by [confirmationLines]. */
class ConfirmationLinesBuilder {

    private val lines = mutableListOf<ConfirmationLine>()

    fun line(role: ConfirmationRole, text: String) {
        lines.add(ConfirmationLine(role, text))
    }

    fun build(): List<ConfirmationLine> = lines
}

/** Build a list of [ConfirmationLine]s. Each [text] must already be a fully localized string. */
fun confirmationLines(block: ConfirmationLinesBuilder.() -> Unit): List<ConfirmationLine> =
    ConfirmationLinesBuilder().apply(block).build()
