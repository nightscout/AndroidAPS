package app.aaps.plugins.constraints.dstHelper.keys

import app.aaps.core.keys.LongNonPreferenceKey

enum class DstHelperLongKey(
    override val key: String,
    override val defaultValue: Long,
) : LongNonPreferenceKey {

    SnoozeDstIn24h(key = "snooze_dst_in24h", 0L),
    SnoozeLoopDisabled(key = "snooze_loop_disabled", 0L),
}
