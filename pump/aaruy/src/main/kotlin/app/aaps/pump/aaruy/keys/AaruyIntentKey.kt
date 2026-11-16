package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.aaruy.R

enum class AaruyIntentKey(
    override val key: String,
    private val titleResId: Int? = null,
    private val summaryResId: Int? = null,
    private val entriesResIds: Map<Int, Int> = emptyMap(),
) : IntentPreferenceKey {
    BtSelector(key = "key_aaruy_bt_selector")

    ;
    override val title: TextRef = TextRef.AndroidRes(titleResId?:R.string.aaruy_pump_setting)
}