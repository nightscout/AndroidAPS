package app.aaps.pump.insight.descriptors

enum class AlertCategory(val id: Int) {
    REMINDER(227),
    MAINTENANCE(252),
    WARNING(805),
    ERROR(826);

    companion object {

        fun fromId(id: Int) = AlertCategory.entries.firstOrNull { it.id == id }
    }
}