package info.nightscout.androidaps.plugins.pump.eopatch.code

import info.nightscout.androidaps.plugins.pump.eopatch.R

class SettingKeys {
    companion object{
        val LOW_RESERVOIR_REMINDERS: Int = R.string.key_eopatch_low_reservoir_reminders
        val EXPIRATION_REMINDERS: Int = R.string.key_eopatch_expiration_reminders
        val BUZZER_REMINDERS: Int = R.string.key_eopatch_patch_buzzer_reminders

        val PATCH_CONFIG: Int = R.string.key_eopatch_patch_config
        val PATCH_STATE: Int = R.string.key_eopatch_patch_state
        val BOLUS_CURRENT: Int = R.string.key_eopatch_bolus_current
        val NORMAL_BASAL: Int = R.string.key_eopatch_normal_basal
        val TEMP_BASAL: Int = R.string.key_eopatch_temp_basal
        val ALARMS: Int = R.string.key_eopatch_bolus_current
    }
}