package app.aaps.pump.omnipod.common.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.omnipod.common.R

enum class OmnipodIntPreferenceKey(
    override val key: String,
    override val min: Int,
    override val max: Int,
    override val defaultValue: Int,
    private val titleResId: Int,
    private val summaryResId: Int? = null,
    private val entriesResIds: Map<Int, Int> = emptyMap(),
    override val dependency: BooleanPreferenceKey? = null,
) : IntPreferenceKey {

    ExpirationReminderHours(
        "AAPS.Omnipod.expiration_reminder_hours_before_expiry", min = 1, max = 24, defaultValue = 4,
        titleResId = R.string.omnipod_common_preferences_expiration_reminder_hours_before_expiry,
        dependency = OmnipodBooleanPreferenceKey.ExpirationReminder
    ),
    ExpirationAlarmHours(
        "AAPS.Omnipod.expiration_alarm_hours_before_shutdown", min = 1, max = 8, defaultValue = 8,
        titleResId = R.string.omnipod_common_preferences_expiration_alarm_hours_before_shutdown,
        dependency = OmnipodBooleanPreferenceKey.ExpirationAlarm
    ),
    LowReservoirAlertUnits(
        "AAPS.Omnipod.low_reservoir_alert_units", min = 5, max = 50, defaultValue = 20,
        titleResId = R.string.omnipod_common_preferences_low_reservoir_alert_units,
        dependency = OmnipodBooleanPreferenceKey.LowReservoirAlert
    );

    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD
    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val entries: Map<Int, TextRef> = entriesResIds.mapValues { TextRef.AndroidRes(it.value) }
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}
