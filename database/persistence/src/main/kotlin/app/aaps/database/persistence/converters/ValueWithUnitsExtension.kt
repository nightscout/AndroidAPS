package app.aaps.database.persistence.converters

fun app.aaps.database.entities.ValueWithUnit.fromDb(): app.aaps.core.data.ue.ValueWithUnit =
    when (this) {
        is app.aaps.database.entities.ValueWithUnit.Gram                  -> app.aaps.core.data.ue.ValueWithUnit.Gram(value)
        is app.aaps.database.entities.ValueWithUnit.Hour                  -> app.aaps.core.data.ue.ValueWithUnit.Hour(value)
        is app.aaps.database.entities.ValueWithUnit.Insulin               -> app.aaps.core.data.ue.ValueWithUnit.Insulin(value)
        is app.aaps.database.entities.ValueWithUnit.Mgdl                  -> app.aaps.core.data.ue.ValueWithUnit.Mgdl(value)
        is app.aaps.database.entities.ValueWithUnit.Minute                -> app.aaps.core.data.ue.ValueWithUnit.Minute(value)
        is app.aaps.database.entities.ValueWithUnit.Mmoll                 -> app.aaps.core.data.ue.ValueWithUnit.Mmoll(value)
        is app.aaps.database.entities.ValueWithUnit.OfflineEventReason    -> app.aaps.core.data.ue.ValueWithUnit.OEReason(value.fromDb())
        is app.aaps.database.entities.ValueWithUnit.Percent               -> app.aaps.core.data.ue.ValueWithUnit.Percent(value)
        is app.aaps.database.entities.ValueWithUnit.SimpleInt             -> app.aaps.core.data.ue.ValueWithUnit.SimpleInt(value)
        is app.aaps.database.entities.ValueWithUnit.SimpleString          -> app.aaps.core.data.ue.ValueWithUnit.SimpleString(value)
        is app.aaps.database.entities.ValueWithUnit.TherapyEventMeterType -> app.aaps.core.data.ue.ValueWithUnit.TEMeterType(value.fromDb())
        is app.aaps.database.entities.ValueWithUnit.TherapyEventTTReason  -> app.aaps.core.data.ue.ValueWithUnit.TETTReason(value.fromDb())
        is app.aaps.database.entities.ValueWithUnit.TherapyEventType      -> app.aaps.core.data.ue.ValueWithUnit.TEType(value.fromDb())
        is app.aaps.database.entities.ValueWithUnit.Timestamp             -> app.aaps.core.data.ue.ValueWithUnit.Timestamp(value)
        is app.aaps.database.entities.ValueWithUnit.UNKNOWN               -> app.aaps.core.data.ue.ValueWithUnit.UNKNOWN
        is app.aaps.database.entities.ValueWithUnit.UnitPerHour           -> app.aaps.core.data.ue.ValueWithUnit.UnitPerHour(value)
    }

fun app.aaps.core.data.ue.ValueWithUnit.toDb(): app.aaps.database.entities.ValueWithUnit =
    when (this) {
        is app.aaps.core.data.ue.ValueWithUnit.Gram         -> app.aaps.database.entities.ValueWithUnit.Gram(value)
        is app.aaps.core.data.ue.ValueWithUnit.Hour         -> app.aaps.database.entities.ValueWithUnit.Hour(value)
        is app.aaps.core.data.ue.ValueWithUnit.Insulin      -> app.aaps.database.entities.ValueWithUnit.Insulin(value)
        is app.aaps.core.data.ue.ValueWithUnit.Mgdl         -> app.aaps.database.entities.ValueWithUnit.Mgdl(value)
        is app.aaps.core.data.ue.ValueWithUnit.Minute       -> app.aaps.database.entities.ValueWithUnit.Minute(value)
        is app.aaps.core.data.ue.ValueWithUnit.Mmoll        -> app.aaps.database.entities.ValueWithUnit.Mmoll(value)
        is app.aaps.core.data.ue.ValueWithUnit.OEReason     -> app.aaps.database.entities.ValueWithUnit.OfflineEventReason(value.toDb())
        is app.aaps.core.data.ue.ValueWithUnit.Percent      -> app.aaps.database.entities.ValueWithUnit.Percent(value)
        is app.aaps.core.data.ue.ValueWithUnit.SimpleInt    -> app.aaps.database.entities.ValueWithUnit.SimpleInt(value)
        is app.aaps.core.data.ue.ValueWithUnit.SimpleString -> app.aaps.database.entities.ValueWithUnit.SimpleString(value)
        is app.aaps.core.data.ue.ValueWithUnit.TEMeterType  -> app.aaps.database.entities.ValueWithUnit.TherapyEventMeterType(value.toDb())
        is app.aaps.core.data.ue.ValueWithUnit.TETTReason   -> app.aaps.database.entities.ValueWithUnit.TherapyEventTTReason(value.toDb())
        is app.aaps.core.data.ue.ValueWithUnit.TEType       -> app.aaps.database.entities.ValueWithUnit.TherapyEventType(value.toDb())
        is app.aaps.core.data.ue.ValueWithUnit.Timestamp    -> app.aaps.database.entities.ValueWithUnit.Timestamp(value)
        is app.aaps.core.data.ue.ValueWithUnit.UNKNOWN      -> app.aaps.database.entities.ValueWithUnit.UNKNOWN
        is app.aaps.core.data.ue.ValueWithUnit.UnitPerHour  -> app.aaps.database.entities.ValueWithUnit.UnitPerHour(value)
    }

fun List<app.aaps.database.entities.ValueWithUnit>.fromDb(): List<app.aaps.core.data.ue.ValueWithUnit> =
    this.map { it.fromDb() }

fun List<app.aaps.core.data.ue.ValueWithUnit>.toDb(): List<app.aaps.database.entities.ValueWithUnit> =
    this.map { it.toDb() }
