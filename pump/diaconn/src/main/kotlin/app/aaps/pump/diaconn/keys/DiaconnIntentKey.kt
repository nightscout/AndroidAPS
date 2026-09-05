package app.aaps.pump.diaconn.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.diaconn.R

enum class DiaconnIntentKey(
    override val key: String,
    private val titleResId: Int,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    BtSelector(key = "diaconn_bt_selector", titleResId = R.string.selectedpump)
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}
