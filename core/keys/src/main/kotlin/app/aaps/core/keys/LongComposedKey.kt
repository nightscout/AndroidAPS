package app.aaps.core.keys

enum class LongComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Long
) : LongComposedNonPreferenceKey {

    NotificationSnoozedTo("snoozedTo", "%s", 0L)
}