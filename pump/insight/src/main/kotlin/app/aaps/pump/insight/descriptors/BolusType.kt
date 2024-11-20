package app.aaps.pump.insight.descriptors

enum class BolusType(val id: Int, val activeId: Int) {
    STANDARD(31, 227),
    EXTENDED(227, 252),
    MULTIWAVE(252, 805);

    companion object {

        fun fromActiveId(activeId: Int) = BolusType.entries.firstOrNull { it.activeId == activeId }
        fun fromId(id: Int) = BolusType.entries.firstOrNull { it.id == id }

    }
}