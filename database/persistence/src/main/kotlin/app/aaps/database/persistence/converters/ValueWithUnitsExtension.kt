package app.aaps.database.persistence.converters

import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.ValueWithUnit

fun app.aaps.database.entities.ValueWithUnit.fromDb(): app.aaps.core.data.ue.ValueWithUnit =
    when (this) {
        is app.aaps.database.entities.ValueWithUnit.Gram                  -> ValueWithUnit.Gram(value)
        is app.aaps.database.entities.ValueWithUnit.Hour                  -> ValueWithUnit.Hour(value)
        is app.aaps.database.entities.ValueWithUnit.Insulin               -> ValueWithUnit.Insulin(value)
        is app.aaps.database.entities.ValueWithUnit.Mgdl                  -> ValueWithUnit.Mgdl(value)
        is app.aaps.database.entities.ValueWithUnit.Minute                -> ValueWithUnit.Minute(value)
        is app.aaps.database.entities.ValueWithUnit.Mmoll                 -> ValueWithUnit.Mmoll(value)
        is app.aaps.database.entities.ValueWithUnit.RunningModeMode       -> ValueWithUnit.RMMode(value.fromDb())
        is app.aaps.database.entities.ValueWithUnit.Percent               -> ValueWithUnit.Percent(value)
        is app.aaps.database.entities.ValueWithUnit.SimpleInt             -> ValueWithUnit.SimpleInt(value)
        is app.aaps.database.entities.ValueWithUnit.SimpleString          -> ValueWithUnit.SimpleString(value)
        is app.aaps.database.entities.ValueWithUnit.TherapyEventMeterType -> ValueWithUnit.TEMeterType(value.fromDb())
        is app.aaps.database.entities.ValueWithUnit.TherapyEventTTReason  -> ValueWithUnit.TETTReason(value.fromDb())
        is app.aaps.database.entities.ValueWithUnit.TherapyEventType      -> ValueWithUnit.TEType(value.fromDb())
        is app.aaps.database.entities.ValueWithUnit.Timestamp             -> ValueWithUnit.Timestamp(value)
        is app.aaps.database.entities.ValueWithUnit.UnitPerHour           -> ValueWithUnit.UnitPerHour(value)
        is app.aaps.database.entities.ValueWithUnit.TherapyEventLocation  -> ValueWithUnit.TELocation(value.fromDb())
        is app.aaps.database.entities.ValueWithUnit.TherapyEventArrow     -> ValueWithUnit.TEArrow(value.fromDb())
        is app.aaps.database.entities.ValueWithUnit.UNKNOWN               -> ValueWithUnit.UNKNOWN
    }

fun app.aaps.core.data.ue.ValueWithUnit.toDb(): app.aaps.database.entities.ValueWithUnit =
    when (this) {
        is ValueWithUnit.Gram         -> app.aaps.database.entities.ValueWithUnit.Gram(value)
        is ValueWithUnit.Hour         -> app.aaps.database.entities.ValueWithUnit.Hour(value)
        is ValueWithUnit.Insulin      -> app.aaps.database.entities.ValueWithUnit.Insulin(value)
        is ValueWithUnit.Mgdl         -> app.aaps.database.entities.ValueWithUnit.Mgdl(value)
        is ValueWithUnit.Minute       -> app.aaps.database.entities.ValueWithUnit.Minute(value)
        is ValueWithUnit.Mmoll        -> app.aaps.database.entities.ValueWithUnit.Mmoll(value)
        is ValueWithUnit.RMMode       -> app.aaps.database.entities.ValueWithUnit.RunningModeMode(value.toDb())
        is ValueWithUnit.Percent      -> app.aaps.database.entities.ValueWithUnit.Percent(value)
        is ValueWithUnit.SimpleInt    -> app.aaps.database.entities.ValueWithUnit.SimpleInt(value)
        is ValueWithUnit.SimpleString -> app.aaps.database.entities.ValueWithUnit.SimpleString(value)
        is ValueWithUnit.TEMeterType  -> app.aaps.database.entities.ValueWithUnit.TherapyEventMeterType(value.toDb())
        is ValueWithUnit.TETTReason   -> app.aaps.database.entities.ValueWithUnit.TherapyEventTTReason(value.toDb())
        is ValueWithUnit.TEType       -> app.aaps.database.entities.ValueWithUnit.TherapyEventType(value.toDb())
        is ValueWithUnit.Timestamp    -> app.aaps.database.entities.ValueWithUnit.Timestamp(value)
        is ValueWithUnit.UnitPerHour  -> app.aaps.database.entities.ValueWithUnit.UnitPerHour(value)
        is ValueWithUnit.TEArrow      -> app.aaps.database.entities.ValueWithUnit.TherapyEventArrow(value.toDb())
        is ValueWithUnit.TELocation   -> app.aaps.database.entities.ValueWithUnit.TherapyEventLocation(value.toDb())
        is ValueWithUnit.UNKNOWN      -> app.aaps.database.entities.ValueWithUnit.UNKNOWN
    }

fun List<app.aaps.database.entities.ValueWithUnit>.fromDb(): List<app.aaps.core.data.ue.ValueWithUnit> =
    this.map { it.fromDb() }

fun List<app.aaps.core.data.ue.ValueWithUnit>.toDb(): List<app.aaps.database.entities.ValueWithUnit> =
    this.map { it.toDb() }
