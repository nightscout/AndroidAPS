package app.aaps.database.transactions

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.aaps.database.AppDatabase
import app.aaps.database.AppRepository
import app.aaps.database.dao.HeartRateDaoTest
import app.aaps.database.entities.HeartRate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InsertOrUpdateHeartRateTransactionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var repo: AppRepository

    @Before
    fun setupUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = AppRepository(db)
    }

    @After
    fun shutdown() {
        db.close()
    }

    @Test
    fun createNewEntry() {
        val hr1 = HeartRateDaoTest.createHeartRate()
        val result = repo.runTransactionForResult(InsertOrUpdateHeartRateTransaction(hr1)).blockingGet()
        assertEquals(listOf(hr1), result.inserted)
        assertTrue(result.updated.isEmpty())
    }

    @Test
    fun updateEntry() {
        val hr1 = HeartRateDaoTest.createHeartRate()
        val id = db.heartRateDao.insertNewEntry(hr1)
        assertNotEquals(0, id)
        val hr2 = hr1.copy(id = id, beatsPerMinute = 181.0)
        val result = repo.runTransactionForResult(InsertOrUpdateHeartRateTransaction(hr2)).blockingGet()
        assertEquals(listOf(hr2), result.updated)
        assertTrue(result.inserted.isEmpty())

        val hr3 = db.heartRateDao.findById(id)!!
        assertTrue(hr2.contentEqualsTo(hr3))
    }

    private fun HeartRate.contentEqualsTo(other: HeartRate): Boolean {
        return this === other || (
            duration == other.duration &&
                timestamp == other.timestamp &&
                beatsPerMinute == other.beatsPerMinute &&
                isValid == other.isValid)
    }
}
