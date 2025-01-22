package app.aaps.plugins.constraints.objectives.keys

import app.aaps.core.keys.LongComposedNonPreferenceKey

enum class ObjectivesLongComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Long
) : LongComposedNonPreferenceKey {

    Started("Objectives_", "%s_started", 0L),
    Accomplished("Objectives_", "%s_accomplished", 0L),
    DisabledTo("DisabledTo_", "%s", 0L),
}