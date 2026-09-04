package app.aaps.pump.diaconn.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.diaconn.R

enum class DiaconnBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    private val titleResId: Int,
    private val summaryResId: Int? = null,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {

    LogInsulinChange("diaconn_g8_loginsulinchange", true, titleResId = R.string.diaconn_g8_loginsulinchange_title, summaryResId = R.string.diaconn_g8_loginsulinchange_summary),
    LogCannulaChange("diaconn_g8_logneedlechange", true, titleResId = R.string.diaconn_g8_logcanulachange_title, summaryResId = R.string.diaconn_g8_logcanulachange_summary),
    LogTubeChange("diaconn_g8_logtubechange", true, titleResId = R.string.diaconn_g8_logtubechange_title, summaryResId = R.string.diaconn_g8_logtubechange_summary),
    LogBatteryChange("diaconn_g8_logbatterychanges", true, titleResId = R.string.diaconn_g8_logbatterychange_title, summaryResId = R.string.diaconn_g8_logbatterychange_summary),
    SendLogsToCloud("diaconn_g8_cloudsend", true, titleResId = R.string.diaconn_g8_cloudsend_title, summaryResId = R.string.diaconn_g8_cloudsend_summary),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}
