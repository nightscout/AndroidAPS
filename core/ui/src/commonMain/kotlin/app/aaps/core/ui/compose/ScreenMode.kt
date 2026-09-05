package app.aaps.core.ui.compose

/**
 * Screen mode for management screens (TT, QuickWizard, Profile).
 *
 * - [PLAY]: Activate/execute only — editing controls hidden, lighter protection (BOLUS or NONE).
 * - [EDIT]: Full editing — all controls visible, PREFERENCES protection.
 *
 * Mode switch is one-way: PLAY → EDIT (triggers PREFERENCES protection).
 */
enum class ScreenMode {

    PLAY, EDIT;

    companion object {

        fun fromRoute(value: String?): ScreenMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: EDIT
    }
}
