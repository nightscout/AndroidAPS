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
        fun fromId(id: Int) = values().firstOrNull { it.id == id }

    }
}