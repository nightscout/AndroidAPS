package info.nightscout.androidaps.plugins.pump.eopatch.code

enum class UnitOrPercent {
    P,
    U;

    fun isPercentage() = this == P
}
