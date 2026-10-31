package app.aaps.plugins.aps.openAPS

data class TddStatus(
    val tdd1D: Double,
    val tdd7D: Double,
    val tddLast24H: Double,
    val tddLast4H: Double,
    val tddLast8to4H: Double
)