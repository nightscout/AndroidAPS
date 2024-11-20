package app.aaps.pump.insight.descriptors

enum class BatteryType(val id: Int) {
    ALKALI(31),
    LITHIUM(227),
    NI_MH(252);

    companion object {

        fun fromId(id: Int) = BatteryType.entries.firstOrNull { it.id == id }
    }
}