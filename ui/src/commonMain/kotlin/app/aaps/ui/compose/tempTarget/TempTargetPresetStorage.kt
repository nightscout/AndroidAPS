package app.aaps.ui.compose.tempTarget

import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.tempTargets.toTTPresets as coreTTPresets
import app.aaps.core.ui.CoreUiStrings

/**
 * The name of a fixed (non-deletable) preset, from its reason. Custom presets carry their own
 * [TTPreset.name] and get null here.
 *
 * This used to hand back an `R.string` id, which meant a multiplatform data class held an Android
 * resource id - and one that could not be stored, since those ids change between builds. Resolving
 * the text here instead keeps that Android detail on this side of the line.
 */
private fun displayNameFromReason(reason: TT.Reason, isDeletable: Boolean, rh: TextResolver): String? {
    if (isDeletable) return null
    return when (reason) {
        TT.Reason.EATING_SOON  -> rh.gs(CoreUiStrings.eatingsoon)
        TT.Reason.ACTIVITY     -> rh.gs(CoreUiStrings.activity)
        TT.Reason.HYPOGLYCEMIA -> rh.gs(CoreUiStrings.hypo)
        else                   -> null
    }
}

/** Fill in the display name of every fixed preset. */
fun List<TTPreset>.withDisplayName(rh: TextResolver): List<TTPreset> =
    map { it.copy(displayName = displayNameFromReason(it.reason, it.isDeletable, rh)) }

/**
 * Read the stored presets and give the fixed ones their display name.
 *
 * The name is resolved now rather than when it is drawn, so the list has to be read again after a
 * language change - which is what happens anyway, because changing the language recreates the
 * activity.
 */
fun String.toTTPresetsWithDisplayName(rh: TextResolver): List<TTPreset> =
    coreTTPresets().withDisplayName(rh)
