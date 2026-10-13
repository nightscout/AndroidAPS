package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.UserEntryDao
import app.aaps.database.entities.UserEntry
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class UserEntryTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var userEntryDao: UserEntryDao

    @BeforeEach
    fun setup() {
        userEntryDao = mock()
        database = mock()
        whenever(database.userEntryDao).thenReturn(userEntryDao)
    }

    @Test
    fun `inserts single user entry`() {
        val entry = createUserEntry("Test action")

        val transaction = UserEntryTransaction(listOf(entry))
        transaction.database = database
        val result = transaction.run()

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(entry)

        verify(userEntryDao).insert(entry)
    }

    @Test
    fun `inserts multiple user entries`() {
        val entry1 = createUserEntry("Action 1")
        val entry2 = createUserEntry("Action 2")

        val transaction = UserEntryTransaction(listOf(entry1, entry2))
        transaction.database = database
        val result = transaction.run()

        assertThat(result).hasSize(2)

        verify(userEntryDao).insert(entry1)
        verify(userEntryDao).insert(entry2)
    }

    private fun createUserEntry(action: String): UserEntry = UserEntry(
        timestamp = System.currentTimeMillis(),
        action = UserEntry.Action.BOLUS,
        source = UserEntry.Sources.Unknown,
        note = action,
        values = emptyList()
    )
}
