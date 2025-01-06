package app.aaps.pump.insight.database

class InsightDbHelper(val insightDatabaseDao: InsightDatabaseDao) {

    fun getInsightBolusID(pumpSerial: String, bolusID: Int, timestamp: Long): InsightBolusID? = insightDatabaseDao.getInsightBolusID(pumpSerial, bolusID, timestamp)

    fun createOrUpdate(insightBolusID: InsightBolusID) = insightDatabaseDao.createOrUpdate(insightBolusID)

    fun getInsightHistoryOffset(pumpSerial: String): InsightHistoryOffset? = insightDatabaseDao.getInsightHistoryOffset(pumpSerial)

    fun createOrUpdate(insightHistoryOffset: InsightHistoryOffset) = insightDatabaseDao.createOrUpdate(insightHistoryOffset)

    fun getPumpStoppedEvent(pumpSerial: String, timestamp: Long): InsightPumpID? =
        insightDatabaseDao.getPumpStoppedEvent(pumpSerial, timestamp, InsightPumpID.EventType.PumpStopped, InsightPumpID.EventType.PumpPaused)

    fun createOrUpdate(insightPumpID: InsightPumpID) = insightDatabaseDao.createOrUpdate(insightPumpID)

}