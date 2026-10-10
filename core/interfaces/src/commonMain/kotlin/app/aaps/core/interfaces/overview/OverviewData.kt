package app.aaps.core.interfaces.overview

interface OverviewData {

    var toTime: Long  // current time rounded up to 1 hour
    var fromTime: Long // toTime - range
    var endTime: Long // toTime + predictions
}
