package app.aaps.pump.omnipod.dash.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.dash.history.database.DashHistoryDatabase
import app.aaps.pump.omnipod.dash.history.database.HistoryRecordDao
import app.aaps.pump.omnipod.dash.history.mapper.HistoryMapper
import app.aaps.shared.tests.AAPSLoggerTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashHistoryTest {

    private lateinit var dao: HistoryRecordDao
    private lateinit var database: DashHistoryDatabase
    private lateinit var dashHistory: DashHistory

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            DashHistoryDatabase::class.java
        ).build()
        dao = database.historyRecordDao()
        dashHistory = DashHistory(dao, HistoryMapper(), AAPSLoggerTest())
    }

    @Test
    fun testInsertionAndConverters() {
        dashHistory.getRecords().test().apply {
            assertValue { it.isEmpty() }
        }

        dashHistory.createRecord(commandType = OmnipodCommandType.CANCEL_BOLUS, 0L).test().apply {
            assertValue { it != 0L }
        }

        dashHistory.getRecords().test().apply {
            assertValue { it.size == 1 }
        }
    }

    @Test
    fun testExceptionOnBolusWithoutRecord() {
        dashHistory.getRecords().test().apply {
            assertValue { it.isEmpty() }
        }

        dashHistory.createRecord(commandType = OmnipodCommandType.SET_BOLUS, 0L).test().apply {
            assertError(IllegalArgumentException::class.java)
        }

        dashHistory.getRecords().test().apply {
            assertValue { it.isEmpty() }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }
}
