package info.nightscout.androidaps.database.transactions

class InvalidateMealLinkTransaction(val id: Long) : Transaction<Unit>() {

    override fun run() {
        val mealLink = database.mealLinkDao.findById(id)
            ?: throw IllegalArgumentException("There is no such MealLink with the specified ID.")

        if (mealLink.bolusId != null) {
            val bolus = database.bolusDao.findById(id)
                ?: throw IllegalArgumentException("There is no such Bolus with the specified ID.")
            bolus.isValid = false
            database.bolusDao.updateExistingEntry(bolus)
        }

        if (mealLink.carbsId != null) {
            val carbs = database.carbsDao.findById(id)
                ?: throw IllegalArgumentException("There is no such Carbs with the specified ID.")
            carbs.isValid = false
            database.carbsDao.updateExistingEntry(carbs)
        }

        if (mealLink.bolusCalcResultId != null) {
            val bolusCalculatorResult = database.bolusCalculatorResultDao.findById(id)
                ?: throw IllegalArgumentException("There is no such BolusCalculatorResult with the specified ID.")
            bolusCalculatorResult.isValid = false
            database.bolusCalculatorResultDao.updateExistingEntry(bolusCalculatorResult)
        }

        // TemporaryBasal is not invalidated for safety reason

        if (mealLink.noteId != null) {
            val therapyEvent = database.therapyEventDao.findById(id)
                ?: throw IllegalArgumentException("There is no such TherapyEvent with the specified ID.")
            therapyEvent.isValid = false
            database.therapyEventDao.updateExistingEntry(therapyEvent)
        }

        mealLink.isValid = false
        database.mealLinkDao.updateExistingEntry(mealLink)
    }
}