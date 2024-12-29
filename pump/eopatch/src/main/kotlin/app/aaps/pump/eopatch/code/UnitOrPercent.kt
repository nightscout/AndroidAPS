package app.aaps.pump.eopatch.code

enum class UnitOrPercent {
    P,
    U;

    fun isPercentage() = this == P
}
