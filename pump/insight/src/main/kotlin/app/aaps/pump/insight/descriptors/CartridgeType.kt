package app.aaps.pump.insight.descriptors

enum class CartridgeType(val id: Int) {
    PREFILLED(31),
    SELF_FILLED(227);

    companion object {

        fun fromId(id: Int) = CartridgeType.entries.firstOrNull { it.id == id }
    }
}