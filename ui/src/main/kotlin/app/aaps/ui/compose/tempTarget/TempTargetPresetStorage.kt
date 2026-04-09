package app.aaps.ui.compose.tempTarget

import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.interfaces.tempTargets.toTTPresets as coreTTPresets

/**
 * Derive nameRes from TT.Reason for fixed (non-deletable) presets.
 * This avoids persisting Android resource IDs which change between builds.
 */
private fun nameResFromReason(reason: TT.Reason, isDeletable: Boolean): Int? {
    if (isDeletable) return null
    return when (reason) {
        TT.Reason.EATING_SOON  -> app.aaps.core.ui.R.string.eatingsoon
        TT.Reason.ACTIVITY     -> app.aaps.core.ui.R.string.activity
        TT.Reason.HYPOGLYCEMIA -> app.aaps.core.ui.R.string.hypo
        else                   -> null
    }
}

/**
 * Set nameRes on presets based on reason (for UI display).
 */
fun List<TTPreset>.withNameRes(): List<TTPreset> =
    map { it.copy(nameRes = nameResFromReason(it.reason, it.isDeletable)) }

/**
 * Parse JSON string into a list of TTPreset with nameRes populated.
 * Use this in UI layer where display names are needed.
 */
fun String.toTTPresetsWithNameRes(): List<TTPreset> =
    coreTTPresets().withNameRes()
