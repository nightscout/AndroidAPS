package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.*

/**
 * Creates the MealLink
 */
class InsertMealLinkTransaction(
    private val bolus: Bolus? = null,
    private val carbs: Carbs? = null,
    private val bolusCalculatorResult: BolusCalculatorResult? = null,
    private val therapyEvent: TherapyEvent? = null,
    private val superBolusTemporaryBasal: TemporaryBasal? = null
) : Transaction<InsertMealLinkTransaction.TransactionResult>() {

    override fun run(): TransactionResult {
        val result = TransactionResult()

        val bolusId = if (bolus != null) database.bolusDao.insert(bolus) else null
        val carbsId = if (carbs != null) database.carbsDao.insert(carbs) else null
        val bolusCalculatorResultId = if (bolusCalculatorResult != null) database.bolusCalculatorResultDao.insert(bolusCalculatorResult) else null
        val temporaryBasalId = if (superBolusTemporaryBasal != null) database.temporaryBasalDao.insert(superBolusTemporaryBasal) else null
        val therapyEventId = if (therapyEvent != null) database.therapyEventDao.insert(therapyEvent) else null

        val mealLink = MealLink(
            timestamp = System.currentTimeMillis(),
            bolusId = bolusId,
            carbsId = carbsId,
            bolusCalcResultId = bolusCalculatorResultId,
            superbolusTempBasalId = temporaryBasalId,
            noteId = therapyEventId
        )

        database.mealLinkDao.insert(mealLink)
        val full = MealLinkLoaded(mealLink, bolus, carbs, bolusCalculatorResult, superBolusTemporaryBasal, therapyEvent)
        result.inserted.add(full)

        return result
    }

    class TransactionResult {

        val inserted = mutableListOf<MealLinkLoaded>()
    }
}