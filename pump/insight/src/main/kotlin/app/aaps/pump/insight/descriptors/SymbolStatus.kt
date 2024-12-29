package app.aaps.pump.insight.descriptors

enum class SymbolStatus(val id: Int) {
    FULL(31),
    LOW(227),
    EMPTY(252);

    companion object {

        fun fromId(id: Int) = SymbolStatus.entries.firstOrNull { it.id == id }
    }
}