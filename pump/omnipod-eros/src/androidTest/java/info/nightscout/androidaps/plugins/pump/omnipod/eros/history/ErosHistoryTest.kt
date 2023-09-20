package info.nightscout.androidaps.plugins.pump.omnipod.eros.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import info.nightscout.androidaps.plugins.pump.omnipod.eros.definition.PodHistoryEntryType
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryDatabase
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryRecordDao
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryRecordEntity
import org.junit.Assert.assertNotNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ErosHistoryTest {

    private lateinit var dao: ErosHistoryRecordDao
    private lateinit var database: ErosHistoryDatabase
    private lateinit var erosHistory: ErosHistory

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            ErosHistoryDatabase::class.java
        ).build()
        dao = database.historyRecordDao()
        erosHistory = ErosHistory(dao)
    }

    @Test
    fun testInsertionAndRetrieval() {
        var history = erosHistory.getAllErosHistoryRecordsFromTimestamp(0L)
        assert(history.isEmpty())

        val type = PodHistoryEntryType.SET_BOLUS.code.toLong()
        val entity = ErosHistoryRecordEntity(1000L, type)
        erosHistory.create(entity)
        erosHistory.create(ErosHistoryRecordEntity(3000L, PodHistoryEntryType.CANCEL_BOLUS.code.toLong()))

        history = erosHistory.getAllErosHistoryRecordsFromTimestamp(0L)
        assert(history.size == 2)
        assert(type == history.first().podEntryTypeCode)

        val returnedEntity = erosHistory.findErosHistoryRecordByPumpId(entity.pumpId)
        assertNotNull(returnedEntity)
        assert(type == returnedEntity?.podEntryTypeCode)
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }
}
