package app.aaps.pump.insight.descriptors

import app.aaps.pump.insight.app_layer.history.history_events.BasalDeliveryChangedEvent
import app.aaps.pump.insight.app_layer.history.history_events.BolusDeliveredEvent
import app.aaps.pump.insight.app_layer.history.history_events.BolusProgrammedEvent
import app.aaps.pump.insight.app_layer.history.history_events.CannulaFilledEvent
import app.aaps.pump.insight.app_layer.history.history_events.CartridgeInsertedEvent
import app.aaps.pump.insight.app_layer.history.history_events.CartridgeRemovedEvent
import app.aaps.pump.insight.app_layer.history.history_events.DateTimeChangedEvent
import app.aaps.pump.insight.app_layer.history.history_events.DefaultDateTimeSetEvent
import app.aaps.pump.insight.app_layer.history.history_events.EndOfTBREvent
import app.aaps.pump.insight.app_layer.history.history_events.HistoryEvent
import app.aaps.pump.insight.app_layer.history.history_events.OccurrenceOfErrorEvent
import app.aaps.pump.insight.app_layer.history.history_events.OccurrenceOfMaintenanceEvent
import app.aaps.pump.insight.app_layer.history.history_events.OccurrenceOfWarningEvent
import app.aaps.pump.insight.app_layer.history.history_events.OperatingModeChangedEvent
import app.aaps.pump.insight.app_layer.history.history_events.PowerDownEvent
import app.aaps.pump.insight.app_layer.history.history_events.PowerUpEvent
import app.aaps.pump.insight.app_layer.history.history_events.SniffingDoneEvent
import app.aaps.pump.insight.app_layer.history.history_events.StartOfTBREvent
import app.aaps.pump.insight.app_layer.history.history_events.TotalDailyDoseEvent
import app.aaps.pump.insight.app_layer.history.history_events.TubeFilledEvent

class HistoryEvents(val id: Int, val type: Class<out HistoryEvent?>) {

    companion object {

        fun fromId(id: Int): HistoryEvent = when (id) {
            BOLUSDELIVEREDEVENT          -> BolusDeliveredEvent()
            BOLUSPROGRAMMEDEVENT         -> BolusProgrammedEvent()
            CANNULAFILLEDEVENT           -> CannulaFilledEvent()
            DATETIMECHANGEDEVENT         -> DateTimeChangedEvent()
            DEFAULTDATETIMESETEVENT      -> DefaultDateTimeSetEvent()
            ENDOFTBREVENT                -> EndOfTBREvent()
            OCCURRENCEOFERROREVENT       -> OccurrenceOfErrorEvent()
            OCCURRENCEOFMAINTENANCEEVENT -> OccurrenceOfMaintenanceEvent()
            OCCURRENCEOFWARNINGEVENT     -> OccurrenceOfWarningEvent()
            OPERATINGMODECHANGEDEVENT    -> OperatingModeChangedEvent()
            POWERUPEVENT                 -> PowerUpEvent()
            POWERDOWNEVENT               -> PowerDownEvent()
            SNIFFINGDONEEVENT            -> SniffingDoneEvent()
            STARTOFTBREVENT              -> StartOfTBREvent()
            TOTALDAILYDOSEEVENT          -> TotalDailyDoseEvent()
            TUBEFILLEDEVENT              -> TubeFilledEvent()
            CARTRIDGEINSERTEDEVENT       -> CartridgeInsertedEvent()
            CARTRIDGEREMOVEDEVENT        -> CartridgeRemovedEvent()
            BASALDELIVERYCHANGEDEVENT    -> BasalDeliveryChangedEvent()
            else                         -> HistoryEvent()
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