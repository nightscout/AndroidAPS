package app.aaps.core.keys

import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec

@Suppress("SpellCheckingInspection")
enum class IntNonKey(
    override val key: String,
    override val defaultValue: Int,
    override val exportable: Boolean = true,
    override val sync: SyncSpec? = null
) : IntNonPreferenceKey {

    ObjectivesManualEnacts("ObjectivesmanualEnacts", 0),
    TddCycleOffset("tdd_cycle_offset", 0),

    /**
     * How long the sensor in use lasts, in hours, when the master's CGM source knows it (an Eversense E3 180 days, a
     * 365 a year); 0 when it does not. The source writes it and a BG source change clears it. It reaches the clients,
     * so every phone colours the sensor age by the same rule and hides the same manual limits. Not exported: it
     * belongs to the source in use, not to a setup.
     */
    SensorLifetimeHours("sensor_lifetime_hours", 0, exportable = false, sync = SyncSpec(SyncChannel.Cold, SyncDirection.MasterOnly))
}
