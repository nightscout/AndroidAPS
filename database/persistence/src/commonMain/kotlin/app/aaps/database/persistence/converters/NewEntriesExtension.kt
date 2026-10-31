package app.aaps.database.persistence.converters

import app.aaps.core.data.model.NE
import app.aaps.database.entities.data.NewEntries

fun NewEntries.fromDb(): NE =
    NE(
        bolusCalculatorResults = this.bolusCalculatorResults.asSequence().map { it.fromDb() }.toList(),
        boluses = this.boluses.asSequence().map { it.fromDb() }.toList(),
        carbs = this.carbs.asSequence().map { it.fromDb() }.toList(),
        effectiveProfileSwitches = this.effectiveProfileSwitches.asSequence().map { it.fromDb() }.toList(),
        extendedBoluses = this.extendedBoluses.asSequence().map { it.fromDb() }.toList(),
        glucoseValues = this.glucoseValues.asSequence().map { it.fromDb() }.toList(),
        runningModes = this.runningModes.asSequence().map { it.fromDb() }.toList(),
        profileSwitches = this.profileSwitches.asSequence().map { it.fromDb() }.toList(),
        temporaryBasals = this.temporaryBasals.asSequence().map { it.fromDb() }.toList(),
        temporaryTarget = this.temporaryTarget.asSequence().map { it.fromDb() }.toList(),
        therapyEvents = this.therapyEvents.asSequence().map { it.fromDb() }.toList(),
        totalDailyDoses = this.totalDailyDoses.asSequence().map { it.fromDb() }.toList(),
        heartRates = this.heartRates.asSequence().map { it.fromDb() }.toList()
    )
