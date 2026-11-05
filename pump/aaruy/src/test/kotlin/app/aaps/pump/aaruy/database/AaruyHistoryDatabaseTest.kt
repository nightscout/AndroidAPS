package app.aaps.pump.aaruy.database

import android.content.Context
import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The queries the driver and the history screen use, on a real Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyHistoryDatabaseTest {

    private val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication() as Context, AaruyHistoryDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val history = database.historyUnitInfoDao()
    private val daily = database.historyDailyUnitInfoDao()

    @After
    fun tearDown() = database.close()

    private fun entry(time: Long, type: Int, reserved: Int = 0) = HistoryUnitInfo().also {
        it.time = time
        it.type = type
        it.reserved = reserved
    }

    /** The pump sends its last entry again at each read. `reserved` tells two entries of one second apart. */
    @Test
    fun countFindsAnEntryOnlyWithTheSameTimeTypeAndCounter() {
        history.insert(entry(1000, 2, reserved = 4))
        assertEquals(1, history.count(1000, 2, 4))
        assertEquals(0, history.count(1000, 2, 8))
        assertEquals(0, history.count(1000, 0, 4))
        assertEquals(0, history.count(2000, 2, 4))
    }

    @Test
    fun querySinceGivesTheNewestFirstAndNothingOlder() {
        history.insert(entry(1000, 0))
        history.insert(entry(3000, 1))
        history.insert(entry(2000, 2))
        assertEquals(listOf(3000L, 2000L), history.querySince(2000).map { it.time })
    }

    @Test
    fun aDayIsFoundByItsStartAndThePump() {
        daily.insert(HistoryDailyUnitInfo().also {
            it.dateTimestamp = 86_400_000
            it.pumpName = "X10001Y"
            it.dailyTotal = 45
        })
        assertEquals(45, daily.find(86_400_000, "X10001Y")?.dailyTotal)
        assertNull(daily.find(86_400_000, "X99999Y"))
        assertNull(daily.find(0, "X10001Y"))
    }

    /** The pump sends today again and again while the day grows, so the row is updated, not added. */
    @Test
    fun anUpdatedDayStaysOneRow() {
        daily.insert(HistoryDailyUnitInfo().also {
            it.dateTimestamp = 86_400_000
            it.pumpName = "X10001Y"
            it.dailyTotal = 45
        })
        val row = daily.find(86_400_000, "X10001Y")!!
        row.dailyTotal = 115
        daily.update(row)
        assertEquals(listOf(115), daily.queryAll().map { it.dailyTotal })
    }
}
