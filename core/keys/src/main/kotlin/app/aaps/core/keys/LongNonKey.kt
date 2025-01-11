package app.aaps.core.keys

enum class LongNonKey(
    override val key: String,
    override val defaultValue: Long
) : LongNonPreferenceKey {

    LocalProfileLastChange("local_profile_last_change", 0L),
}

