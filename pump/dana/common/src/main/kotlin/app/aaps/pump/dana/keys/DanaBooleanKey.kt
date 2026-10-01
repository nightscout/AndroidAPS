package app.aaps.pump.dana.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.dana.R

enum class DanaBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    private val titleResId: Int,
    private val summaryResId: Int? = null,
    override val defaultedBySM: Boolean = false,
) : BooleanPreferenceKey {

    UseExtended("danar_useextended", true, titleResId = R.string.danar_useextended_title, defaultedBySM = true),
    LogCannulaChange("rs_logcanulachange", true, titleResId = R.string.rs_logcanulachange_title, summaryResId = R.string.rs_logcanulachange_summary),
    LogInsulinChange("rs_loginsulinchange", true, titleResId = R.string.rs_loginsulinchange_title, summaryResId = R.string.rs_loginsulinchange_summary),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}
