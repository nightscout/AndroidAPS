package app.aaps.database.persistence.converters

import app.aaps.core.data.model.TrendArrow
import app.aaps.database.entities.GlucoseValue

fun GlucoseValue.TrendArrow.fromDb(): TrendArrow =
    when (this) {
        GlucoseValue.TrendArrow.TRIPLE_DOWN     -> TrendArrow.TRIPLE_DOWN
        GlucoseValue.TrendArrow.DOUBLE_DOWN     -> TrendArrow.DOUBLE_DOWN
        GlucoseValue.TrendArrow.SINGLE_DOWN     -> TrendArrow.SINGLE_DOWN
        GlucoseValue.TrendArrow.FORTY_FIVE_DOWN -> TrendArrow.FORTY_FIVE_DOWN
        GlucoseValue.TrendArrow.FLAT            -> TrendArrow.FLAT
        GlucoseValue.TrendArrow.FORTY_FIVE_UP   -> TrendArrow.FORTY_FIVE_UP
        GlucoseValue.TrendArrow.SINGLE_UP       -> TrendArrow.SINGLE_UP
        GlucoseValue.TrendArrow.DOUBLE_UP       -> TrendArrow.DOUBLE_UP
        GlucoseValue.TrendArrow.TRIPLE_UP       -> TrendArrow.TRIPLE_UP
        GlucoseValue.TrendArrow.NONE            -> TrendArrow.NONE
    }

fun TrendArrow.toDb(): GlucoseValue.TrendArrow =
    when (this) {
        TrendArrow.TRIPLE_DOWN     -> GlucoseValue.TrendArrow.TRIPLE_DOWN
        TrendArrow.DOUBLE_DOWN     -> GlucoseValue.TrendArrow.DOUBLE_DOWN
        TrendArrow.SINGLE_DOWN     -> GlucoseValue.TrendArrow.SINGLE_DOWN
        TrendArrow.FORTY_FIVE_DOWN -> GlucoseValue.TrendArrow.FORTY_FIVE_DOWN
        TrendArrow.FLAT            -> GlucoseValue.TrendArrow.FLAT
        TrendArrow.FORTY_FIVE_UP   -> GlucoseValue.TrendArrow.FORTY_FIVE_UP
        TrendArrow.SINGLE_UP       -> GlucoseValue.TrendArrow.SINGLE_UP
        TrendArrow.DOUBLE_UP       -> GlucoseValue.TrendArrow.DOUBLE_UP
        TrendArrow.TRIPLE_UP       -> GlucoseValue.TrendArrow.TRIPLE_UP
        TrendArrow.NONE            -> GlucoseValue.TrendArrow.NONE
    }

