package app.aaps.core.interfaces.aps

data class CurrentTemp(
    var duration: Int,
    var rate: Double,
    var minutesrunning: Int?
)