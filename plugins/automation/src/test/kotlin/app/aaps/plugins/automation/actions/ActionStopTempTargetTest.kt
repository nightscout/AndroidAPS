package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.queue.Callback
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.impl.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import app.aaps.database.impl.transactions.Transaction
import app.aaps.plugins.automation.R
import io.reactivex.rxjava3.core.Single
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.skyscreamer.jsonassert.JSONAssert

class ActionStopTempTargetTest : ActionsTestBase() {

    private lateinit var sut: ActionStopTempTarget

    @BeforeEach
    fun setup() {
        `when`(rh.gs(app.aaps.core.ui.R.string.stoptemptarget)).thenReturn("Stop temp target")

        sut = ActionStopTempTarget(injector)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(app.aaps.core.ui.R.string.stoptemptarget)
    }

    @Test fun shortDescriptionTest() {
        assertThat(sut.shortDescription()).isEqualTo("Stop temp target")
    }

    @Test fun iconTest() {
        assertThat(sut.icon()).isEqualTo(R.drawable.ic_stop_24dp)
    }

    @Test fun doActionTest() {
        val inserted = mutableListOf<TemporaryTarget>().apply {
            // insert all inserted TTs
        }
        val updated = mutableListOf<TemporaryTarget>().apply {
            // add(TemporaryTarget(id = 0, version = 0, dateCreated = 0, isValid = false, referenceId = null, interfaceIDs_backing = null, timestamp = 0, utcOffset = 0, reason =, highTarget = 0.0, lowTarget = 0.0, duration = 0))
            // insert all updated TTs
        }
        `when`(
            repository.runTransactionForResult(anyObject<Transaction<CancelCurrentTemporaryTargetIfAnyTransaction.TransactionResult>>())
        ).thenReturn(Single.just(CancelCurrentTemporaryTargetIfAnyTransaction.TransactionResult().apply {
            inserted.addAll(inserted)
            updated.addAll(updated)
        }))

        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
        Mockito.verify(repository, Mockito.times(1)).runTransactionForResult((anyObject<Transaction<CancelCurrentTemporaryTargetIfAnyTransaction.TransactionResult>>()))
    }

    @Test fun hasDialogTest() {
        assertThat(sut.hasDialog()).isFalse()
    }

    @Test fun toJSONTest() {
        JSONAssert.assertEquals("""{"type":"ActionStopTempTarget"}""", sut.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("""{"reason":"Test"}""")
        assertThat(sut).isNotNull()
    }
}
