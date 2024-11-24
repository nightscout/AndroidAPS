package app.aaps.core.interfaces.rx.weardata

import androidx.annotation.StringRes
import app.aaps.core.interfaces.R

enum class CwfMetadataKey(val key: String, @StringRes val label: Int, val isPref: Boolean) {

    CWF_NAME("name", R.string.metadata_label_watchface_name, false),
    CWF_FILENAME("filename", R.string.metadata_wear_import_filename, false),
    CWF_AUTHOR("author", R.string.metadata_label_watchface_author, false),
    CWF_CREATED_AT("created_at", R.string.metadata_label_watchface_created_at, false),
    CWF_VERSION("cwf_version", R.string.metadata_label_plugin_version, false),
    CWF_AUTHOR_VERSION("author_version", R.string.metadata_label_watchface_name_version, false),
    CWF_COMMENT("comment", R.string.metadata_label_watchface_infos, false),
    CWF_AUTHORIZATION("cwf_authorization", R.string.metadata_label_watchface_authorization, false),
    CWF_PREF_WATCH_SHOW_DETAILED_IOB("key_show_detailed_iob", R.string.pref_show_detailed_iob, true),
    CWF_PREF_WATCH_SHOW_DETAILED_DELTA("key_show_detailed_delta", R.string.pref_show_detailed_delta, true),
    CWF_PREF_WATCH_SHOW_BGI("key_show_bgi", R.string.pref_show_bgi, true),
    CWF_PREF_WATCH_SHOW_IOB("key_show_iob", R.string.pref_show_iob, true),
    CWF_PREF_WATCH_SHOW_COB("key_show_cob", R.string.pref_show_cob, true),
    CWF_PREF_WATCH_SHOW_DELTA("key_show_delta", R.string.pref_show_delta, true),
    CWF_PREF_WATCH_SHOW_AVG_DELTA("key_show_avg_delta", R.string.pref_show_avgdelta, true),
    CWF_PREF_WATCH_SHOW_TEMP_TARGET("key_show_temp_target", R.string.pref_show_tempTarget ,true),
    CWF_PREF_WATCH_SHOW_RESERVOIR_LEVEL("key_show_reservoir_level", R.string.pref_show_reservoir_level ,true),
    CWF_PREF_WATCH_SHOW_UPLOADER_BATTERY("key_show_uploader_battery", R.string.pref_show_phone_battery, true),
    CWF_PREF_WATCH_SHOW_RIG_BATTERY("key_show_rig_battery", R.string.pref_show_rig_battery, true),
    CWF_PREF_WATCH_SHOW_TEMP_BASAL("key_show_temp_basal", R.string.pref_show_basal_rate, true),
    CWF_PREF_WATCH_SHOW_DIRECTION("key_show_direction", R.string.pref_show_direction_arrow, true),
    CWF_PREF_WATCH_SHOW_AGO("key_show_ago", R.string.pref_show_ago, true),
    CWF_PREF_WATCH_SHOW_BG("key_show_bg", R.string.pref_show_bg, true),
    CWF_PREF_WATCH_SHOW_LOOP_STATUS("key_show_loop_status", R.string.pref_show_loop_status, true),
    CWF_PREF_WATCH_SHOW_WEEK_NUMBER("key_show_week_number", R.string.pref_show_week_number, true),
    CWF_PREF_WATCH_SHOW_DATE("key_show_date", R.string.pref_show_date, true);

    companion object {

        fun fromKey(key: String): CwfMetadataKey? =
            entries.firstOrNull { it.key == key }
    }
}

typealias CwfMetadataMap = MutableMap<CwfMetadataKey, String>