package app.aaps.core.main.extensions

import app.aaps.core.data.db.GlucoseUnit

fun app.aaps.database.entities.data.GlucoseUnit.fromDb(): GlucoseUnit =
    when (this) {
        app.aaps.database.entities.data.GlucoseUnit.MGDL -> GlucoseUnit.MGDL
        app.aaps.database.entities.data.GlucoseUnit.MMOL -> GlucoseUnit.MMOL
    }

fun GlucoseUnit.toDb(): app.aaps.database.entities.data.GlucoseUnit =
    when (this) {
        GlucoseUnit.MGDL -> app.aaps.database.entities.data.GlucoseUnit.MGDL
        GlucoseUnit.MMOL -> app.aaps.database.entities.data.GlucoseUnit.MMOL
    }

