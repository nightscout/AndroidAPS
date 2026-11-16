package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.aaruy.R

enum class AaruyBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    private val titleResId: Int? = null,
    private val summaryResId: Int? = null,
    private val entriesResIds: Map<Int, Int> = emptyMap(),
) : BooleanPreferenceKey {
    PumpSaveHistory("key_aaruy_save_history", false),
    PumpAlreadyBack("key_already_back", false),
    ;


    override val title: TextRef = TextRef.AndroidRes(titleResId?:R.string.aaruy_pump_setting)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}