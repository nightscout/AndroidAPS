package info.nightscout.androidaps.plugins.pump.insight.descriptors

enum class AlertStatus (val id: Int)  {
    ACTIVE (31),
    SNOOZED (227);

    companion object {
        fun fromId(id: Int) = values().firstOrNull { it.id == id }
    }
}