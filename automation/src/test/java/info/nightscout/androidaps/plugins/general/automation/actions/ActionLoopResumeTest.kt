package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.transactions.CancelCurrentOfflineEventIfAnyTransaction
import info.nightscout.androidaps.database.transactions.Transaction
import info.nightscout.androidaps.queue.Callback
import io.reactivex.Single
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class ActionLoopResumeTest : ActionsTestBase() {

    lateinit var sut: ActionLoopResume

    @Before
    fun setup() {

        `when`(resourceHelper.gs(R.string.resumeloop)).thenReturn("Resume loop")
        `when`(resourceHelper.gs(R.string.notsuspended)).thenReturn("Not suspended")

        sut = ActionLoopResume(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.resumeloop, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assert.assertEquals("Resume loop", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.ic_replay_24dp, sut.icon())
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