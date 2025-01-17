package app.aaps.pump.insight.descriptors

enum class OperatingMode(val id: Int) {
    STOPPED(31),
    STARTED(227),
    PAUSED(252);

    companion object {

        fun fromId(id: Int) = OperatingMode.entries.firstOrNull { it.id == id }
    }
}