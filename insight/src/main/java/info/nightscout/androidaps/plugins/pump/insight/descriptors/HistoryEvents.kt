package info.nightscout.androidaps.plugins.pump.insight.descriptors

import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.HistoryEvent

enum class HistoryEvents (val id: Int, val type: Class<out HistoryEvent?>)  {
    BOLUSDELIVEREDEVENT (917, BolusDeliveredEvent::class.java),
    BOLUSPROGRAMMEDEVENT (874, BolusProgrammedEvent::class.java),
    CANNULAFILLEDEVENT (3264, CannulaFilledEvent::class.java),
    DATETIMECHANGEDEVENT (165, DateTimeChangedEvent::class.java),
    DEFAULTDATETIMESETEVENT (170, DefaultDateTimeSetEvent::class.java),
    ENDOFTBREVENT (771, EndOfTBREvent::class.java),
    OCCURRENCEOFERROREVENT (1011, OccurrenceOfErrorEvent::class.java),
    OCCURRENCEOFMAINTENANCEEVENT (1290, OccurrenceOfMaintenanceEvent::class.java),
    OCCURRENCEOFWARNINGEVENT (1360, OccurrenceOfWarningEvent::class.java),
    OPERATINGMODECHANGEDEVENT (195, OperatingModeChangedEvent::class.java),
    POWERUPEVENT (15, PowerUpEvent::class.java),
    POWERDOWNEVENT (51, PowerDownEvent::class.java),
    SNIFFINGDONEEVENT (102, SniffingDoneEvent::class.java),
    STARTOFTBREVENT (240, StartOfTBREvent::class.java),
    TOTALDAILYDOSEEVENT (960, TotalDailyDoseEvent::class.java),
    TUBEFILLEDEVENT (105, TubeFilledEvent::class.java),
    CARTRIDGEINSERTEDEVENT (60, CartridgeInsertedEvent::class.java),
    CARTRIDGEREMOVEDEVENT (85, CartridgeRemovedEvent::class.java),
    BASALDELIVERYCHANGEDEVENT (204, BasalDeliveryChangedEvent::class.java);

    companion object {
        fun fromType(type: Class<out HistoryEvent?>) = values().firstOrNull { it.type == type }

        fun fromId(id: Int) : HistoryEvent = when(id){
            BOLUSDELIVEREDEVENT -> BolusDeliveredEvent()
            BOLUSPROGRAMMEDEVENT -> BolusProgrammedEvent()
            CANNULAFILLEDEVENT -> CannulaFilledEvent()
            DATETIMECHANGEDEVENT -> DateTimeChangedEvent()
            DEFAULTDATETIMESETEVENT -> DefaultDateTimeSetEvent()
            ENDOFTBREVENT -> EndOfTBREvent()
            OCCURRENCEOFERROREVENT -> OccurrenceOfErrorEvent()
            OCCURRENCEOFMAINTENANCEEVENT -> OccurrenceOfMaintenanceEvent()
            OCCURRENCEOFWARNINGEVENT -> OccurrenceOfWarningEvent()
            OPERATINGMODECHANGEDEVENT -> OperatingModeChangedEvent()
            POWERUPEVENT -> PowerUpEvent()
            POWERDOWNEVENT -> PowerDownEvent()
            SNIFFINGDONEEVENT -> SniffingDoneEvent()
            STARTOFTBREVENT -> StartOfTBREvent()
            TOTALDAILYDOSEEVENT -> TotalDailyDoseEvent()
            TUBEFILLEDEVENT -> TubeFilledEvent()
            CARTRIDGEINSERTEDEVENT -> CartridgeInsertedEvent()
            CARTRIDGEREMOVEDEVENT -> CartridgeRemovedEvent()
            BASALDELIVERYCHANGEDEVENT -> BasalDeliveryChangedEvent()
            else -> HistoryEvent()
        }

        const val BOLUSDELIVEREDEVENT = 917
        const val BOLUSPROGRAMMEDEVENT = 874
        const val CANNULAFILLEDEVENT = 3264
        const val DATETIMECHANGEDEVENT = 165
        const val DEFAULTDATETIMESETEVENT = 170
        const val ENDOFTBREVENT = 771
        const val OCCURRENCEOFERROREVENT = 1011
        const val OCCURRENCEOFMAINTENANCEEVENT = 1290
        const val OCCURRENCEOFWARNINGEVENT = 1360
        const val OPERATINGMODECHANGEDEVENT = 195
        const val POWERUPEVENT = 15
        const val POWERDOWNEVENT = 51
        const val SNIFFINGDONEEVENT = 102
        const val STARTOFTBREVENT = 240
        const val TOTALDAILYDOSEEVENT = 960
        const val TUBEFILLEDEVENT = 105
        const val CARTRIDGEINSERTEDEVENT = 60
        const val CARTRIDGEREMOVEDEVENT = 85
        const val BASALDELIVERYCHANGEDEVENT = 204
        

    }
}