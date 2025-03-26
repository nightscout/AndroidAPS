package app.aaps.plugins.constraints.objectives.keys

import app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey

enum class ObjectivesLongComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongComposedNonPreferenceKey {

    Started("Objectives_started_", "%s", 0L),
    Accomplished("Objectives_accomplished_", "%s", 0L),
    DisabledTo("DisabledTo_", "%s", 0L),
}