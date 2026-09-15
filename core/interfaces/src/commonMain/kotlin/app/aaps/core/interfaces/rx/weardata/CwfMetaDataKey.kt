package app.aaps.core.interfaces.rx.weardata

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef

enum class CwfMetadataKey(val key: String, val label: TextRef, val isPref: Boolean) {

    CWF_NAME("name", InterfacesStrings.metadata_label_watchface_name, false),
    CWF_FILENAME("filename", InterfacesStrings.metadata_wear_import_filename, false),
    CWF_AUTHOR("author", InterfacesStrings.metadata_label_watchface_author, false),
    CWF_CREATED_AT("created_at", InterfacesStrings.metadata_label_watchface_created_at, false),
    CWF_VERSION("cwf_version", InterfacesStrings.metadata_label_plugin_version, false),
    CWF_AUTHOR_VERSION("author_version", InterfacesStrings.metadata_label_watchface_name_version, false),
    CWF_COMMENT("comment", InterfacesStrings.metadata_label_watchface_infos, false),
    CWF_AUTHORIZATION("cwf_authorization", InterfacesStrings.metadata_label_watchface_authorization, false),
    CWF_PREF_WATCH_SHOW_DETAILED_IOB("key_show_detailed_iob", InterfacesStrings.pref_show_detailed_iob, true),
    CWF_PREF_WATCH_SHOW_DETAILED_DELTA("key_show_detailed_delta", InterfacesStrings.pref_show_detailed_delta, true),
    CWF_PREF_WATCH_SHOW_BGI("key_show_bgi", InterfacesStrings.pref_show_bgi, true),
    CWF_PREF_WATCH_SHOW_IOB("key_show_iob", InterfacesStrings.pref_show_iob, true),
    CWF_PREF_WATCH_SHOW_COB("key_show_cob", InterfacesStrings.pref_show_cob, true),
    CWF_PREF_WATCH_SHOW_DELTA("key_show_delta", InterfacesStrings.pref_show_delta, true),
    CWF_PREF_WATCH_SHOW_AVG_DELTA("key_show_avg_delta", InterfacesStrings.pref_show_avgdelta, true),
    CWF_PREF_WATCH_SHOW_TEMP_TARGET("key_show_temp_target", InterfacesStrings.pref_show_tempTarget, true),
    CWF_PREF_WATCH_SHOW_RESERVOIR_LEVEL("key_show_reservoir_level", InterfacesStrings.pref_show_reservoir_level, true),
    CWF_PREF_WATCH_SHOW_UPLOADER_BATTERY("key_show_uploader_battery", InterfacesStrings.pref_show_phone_battery, true),
    CWF_PREF_WATCH_SHOW_RIG_BATTERY("key_show_rig_battery", InterfacesStrings.pref_show_rig_battery, true),
    CWF_PREF_WATCH_SHOW_TEMP_BASAL("key_show_temp_basal", InterfacesStrings.pref_show_basal_rate, true),
    CWF_PREF_WATCH_SHOW_DIRECTION("key_show_direction", InterfacesStrings.pref_show_direction_arrow, true),
    CWF_PREF_WATCH_SHOW_AGO("key_show_ago", InterfacesStrings.pref_show_ago, true),
    CWF_PREF_WATCH_SHOW_BG("key_show_bg", InterfacesStrings.pref_show_bg, true),
    CWF_PREF_WATCH_SHOW_LOOP_STATUS("key_show_loop_status", InterfacesStrings.pref_show_loop_status, true),
    CWF_PREF_WATCH_SHOW_WEEK_NUMBER("key_show_week_number", InterfacesStrings.pref_show_week_number, true),
    CWF_PREF_WATCH_SHOW_DATE("key_show_date", InterfacesStrings.pref_show_date, true),
    CWF_PREF_WATCH_SHOW_SECONDS("key_show_seconds", InterfacesStrings.pref_show_seconds, true);

    companion object {

        fun fromKey(key: String): CwfMetadataKey? =
            entries.firstOrNull { it.key == key }
    }
}

typealias CwfMetadataMap = MutableMap<CwfMetadataKey, String>
