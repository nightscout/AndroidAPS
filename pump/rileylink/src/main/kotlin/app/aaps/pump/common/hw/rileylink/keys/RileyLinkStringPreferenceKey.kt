package app.aaps.pump.common.hw.rileylink.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.common.hw.rileylink.R

enum class RileyLinkStringPreferenceKey(
    override val key: String,
    override val defaultValue: String,
    private val titleResId: Int,
    private val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    private val entriesResIds: Map<String, Int> = emptyMap(),
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
) : StringPreferenceKey {

    Encoding(
        key = "pref_medtronic_encoding",
        defaultValue = "medtronic_pump_encoding_4b6b_rileylink",
        titleResId = R.string.medtronic_pump_encoding_title,
        preferenceType = PreferenceType.LIST,
        entriesResIds = mapOf(
            "medtronic_pump_encoding_4b6b_local" to R.string.medtronic_pump_encoding_4b6b_local,
            "medtronic_pump_encoding_4b6b_rileylink" to R.string.medtronic_pump_encoding_4b6b_rileylink
        )
    ),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val entries: Map<String, TextRef> = entriesResIds.mapValues { TextRef.AndroidRes(it.value) }
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}
