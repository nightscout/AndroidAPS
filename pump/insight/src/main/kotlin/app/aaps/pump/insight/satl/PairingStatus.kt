package app.aaps.pump.insight.satl

enum class PairingStatus(val id: Int) {
    CONFIRMED(11835),
    REJECTED(7850),
    PENDING(1683);

    companion object {

        fun fromId(id: Int) = PairingStatus.entries.firstOrNull { it.id == id } ?: PENDING
    }
}