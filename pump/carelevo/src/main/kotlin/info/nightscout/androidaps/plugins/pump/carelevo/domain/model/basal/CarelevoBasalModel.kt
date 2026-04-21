package info.nightscout.androidaps.plugins.pump.carelevo.domain.model.basal

data class CarelevoBasalSegment(
    val injectStartHour : Int,
    val injectStartMin : Int,
    val injectSpeed : Double
)

data class CarelevoBasalSegmentDomainModel(
    val startTime : Int,
    val endTime : Int,
    val speed : Double
)