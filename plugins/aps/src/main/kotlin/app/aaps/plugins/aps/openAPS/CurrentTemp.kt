package app.aaps.plugins.aps.openAPS

data class CurrentTemp(
    var duration: Int,
    var rate: Double,
    var minutesrunning: Int?
)