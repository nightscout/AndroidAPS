package info.nightscout.androidaps.plugins.pump.carelevo.config

class BleEnvConfig {
    companion object {

        const val BLE_CCC_DESCRIPTOR = "00002902-0000-1000-8000-00805f9b34fb"
        const val BLE_SERVICE_UUID = "e1b40001-ffc4-4daa-a49b-1c92f99072ab"
        const val BLE_TX_CHAR_UUID = "e1b40003-ffc4-4daa-a49b-1c92f99072ab"
        const val BLE_RX_CHAR_UUID = "e1b40002-ffc4-4daa-a49b-1c92f99072ab"
    }
}

class PrefEnvConfig {
    companion object {

        const val PATCH_INFO = "carelevo_patch_info"
        const val BASAL_INFUSION_INFO = "carelevo_basal_infusion_info"
        const val TEMP_BASAL_INFUSION_INFO = "carelevo_temp_basal_infusion_info"
        const val IMME_BOLUS_INFUSION_INFO = "carelevo_imme_bolus_infusion_info"
        const val EXTEND_BOLUS_INFUSION_INFO = "carelevo_extend_bolus_infusion_info"
        const val USER_SETTING_INFO = "carelevo_user_setting_info"
        const val CARELEVO_ALARM_INFO_LIST = "carelevo_alarm_info_list"
    }
}