package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.queue.Callback
import app.aaps.database.entities.TemporaryTarget
import app.aaps.plugins.automation.R
import info.nightscout.database.impl.transactions.CancelCurrentOfflineEventIfAnyTransaction
import info.nightscout.database.impl.transactions.Transaction
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class ActionLoopResumeTest : ActionsTestBase() {

    lateinit var sut: ActionLoopResume

    @BeforeEach
    fun setup() {

        `when`(rh.gs(app.aaps.core.ui.R.string.resumeloop)).thenReturn("Resume loop")
        `when`(context.getString(R.string.notsuspended)).thenReturn("Not suspended")

        sut = ActionLoopResume(injector)
    }

    @Test fun friendlyNameTest() {
        Assertions.assertEquals(app.aaps.core.ui.R.string.resumeloop, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assertions.assertEquals("Resume loop", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assertions.assertEquals(R.drawable.ic_replay_24dp, sut.icon())
    }

    @Test fun doActionTest() {
        `when`(loopPlugin.isSuspended).thenReturn(true)
        val inserted = mutableListOf<TemporaryTarget>().apply {
            // insert all inserted TTs
        }
        val updated = mutableListOf<TemporaryTarget>().apply {
            // add(TemporaryTarget(id = 0, version = 0, dateCreated = 0, isValid = false, referenceId = null, interfaceIDs_backing = null, timestamp = 0, utcOffset = 0, reason =, highTarget = 0.0, lowTarget = 0.0, duration = 0))
            // insert all updated TTs
        }
        `when`(
            repository.runTransactionForResult(anyObject<Transaction<CancelCurrentOfflineEventIfAnyTransaction.TransactionResult>>())
        ).thenReturn(Single.just(CancelCurrentOfflineEventIfAnyTransaction.TransactionResult().apply {
            inserted.addAll(inserted)
            updated.addAll(updated)
        }))

        sut.doAction(object : Callback() {
            override fun run() {}
        })
        //Mockito.verify(loopPlugin, Mockito.times(1)).suspendTo(0)

        // another call should keep it resumed, , no new invocation
        `when`(loopPlugin.isSuspended).thenReturn(false)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        //Mockito.verify(loopPlugin, Mockito.times(1)).suspendTo(0)
    }
}