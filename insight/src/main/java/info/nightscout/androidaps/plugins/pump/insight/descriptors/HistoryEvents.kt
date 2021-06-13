package info.nightscout.androidaps.plugins.pump.insight.descriptors

import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.HistoryEvent

class HistoryEvents (val id: Int, val type: Class<out HistoryEvent?>)  {

    companion object {
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