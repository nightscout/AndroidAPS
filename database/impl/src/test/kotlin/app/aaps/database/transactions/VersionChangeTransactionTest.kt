package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.VersionChangeDao
import app.aaps.database.entities.VersionChange
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VersionChangeTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var versionChangeDao: VersionChangeDao

    @BeforeEach
    fun setup() {
        versionChangeDao = mock()
        database = mock()
        whenever(database.versionChangeDao).thenReturn(versionChangeDao)
    }

    @Test
    fun `inserts version change when no previous version exists`() = runTest {
        whenever(versionChangeDao.getMostRecentVersionChange()).thenReturn(null)

        val transaction = VersionChangeTransaction("3.2.0", 320, "origin", "abc123")
        transaction.database = database
        transaction.run()

        verify(versionChangeDao).insert(any<VersionChange>())
    }

    @Test
    fun `inserts version change when version name changes`() = runTest {
        val existing = createVersionChange("3.1.0", 310, "origin", "abc123")
        whenever(versionChangeDao.getMostRecentVersionChange()).thenReturn(existing)

        val transaction = VersionChangeTransaction("3.2.0", 310, "origin", "abc123")
        transaction.database = database
        transaction.run()

        verify(versionChangeDao).insert(any<VersionChange>())
    }

    @Test
    fun `inserts version change when version code changes`() = runTest {
        val existing = createVersionChange("3.2.0", 310, "origin", "abc123")
        whenever(versionChangeDao.getMostRecentVersionChange()).thenReturn(existing)

        val transaction = VersionChangeTransaction("3.2.0", 320, "origin", "abc123")
        transaction.database = database
        transaction.run()

        verify(versionChangeDao).insert(any<VersionChange>())
    }

    @Test
    fun `inserts version change when git remote changes`() = runTest {
        val existing = createVersionChange("3.2.0", 320, "origin", "abc123")
        whenever(versionChangeDao.getMostRecentVersionChange()).thenReturn(existing)

        val transaction = VersionChangeTransaction("3.2.0", 320, "upstream", "abc123")
        transaction.database = database
        transaction.run()

        verify(versionChangeDao).insert(any<VersionChange>())
    }

    @Test
    fun `inserts version change when commit hash changes`() = runTest {
        val existing = createVersionChange("3.2.0", 320, "origin", "abc123")
        whenever(versionChangeDao.getMostRecentVersionChange()).thenReturn(existing)

        val transaction = VersionChangeTransaction("3.2.0", 320, "origin", "def456")
        transaction.database = database
        transaction.run()

        verify(versionChangeDao).insert(any<VersionChange>())
    }

    @Test
    fun `does not insert version change when all values are same`() = runTest {
        val existing = createVersionChange("3.2.0", 320, "origin", "abc123")
        whenever(versionChangeDao.getMostRecentVersionChange()).thenReturn(existing)

        val transaction = VersionChangeTransaction("3.2.0", 320, "origin", "abc123")
        transaction.database = database
        transaction.run()

        verify(versionChangeDao, never()).insert(any<VersionChange>())
    }

    private fun createVersionChange(
        versionName: String,
        versionCode: Int,
        gitRemote: String?,
        commitHash: String?
    ): VersionChange = VersionChange(
        timestamp = System.currentTimeMillis(),
        versionCode = versionCode,
        versionName = versionName,
        gitRemote = gitRemote,
        commitHash = commitHash
    )
}
