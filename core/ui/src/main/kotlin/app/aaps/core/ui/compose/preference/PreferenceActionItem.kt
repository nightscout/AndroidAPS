package app.aaps.core.ui.compose.preference

import androidx.annotation.StringRes
import app.aaps.core.keys.interfaces.PreferenceItem

data class PreferenceActionItem(
    val key: String,
    @StringRes val titleResId: Int,
    @StringRes val summaryResId: Int = 0,
    val onAction: () -> Unit,
) : PreferenceItem
