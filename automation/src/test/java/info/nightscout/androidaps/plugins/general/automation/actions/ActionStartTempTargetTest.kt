package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import info.nightscout.androidaps.database.transactions.Transaction
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.InputTempTarget
import info.nightscout.androidaps.queue.Callback
import io.reactivex.Single
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class ActionStartTempTargetTest : ActionsTestBase() {

    private lateinit var sut: ActionStartTempTarget

    @Before
    fun setup() {
        `when`(resourceHelper.gs(R.string.starttemptarget)).thenReturn("Start temp target")

        sut = ActionStartTempTarget(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.starttemptarget, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.value = InputTempTarget(profileFunction)
        sut.value.value = 100.0
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        Assert.assertEquals("Start temp target: 100mg/dl@null(Automation)", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.ic_temptarget_high, sut.icon())
    }

    @Test fun doActionTest() {

      val expectedTarget = TemporaryTarget(
            id = 0,
            version = 0,
            dateCreated = -1,
            isValid = true,
            referenceId = null,
            interfaceIDs_backing = null,
            timestamp = 0,
            utcOffset = 0,
            reason = TemporaryTarget.Reason.AUTOMATION,
            highTarget = 110.0,
            lowTarget = 110.0,
            duration = 1800000
        )

        val inserted = mutableListOf<TemporaryTarget>().apply {
            add(expectedTarget)
        }

        val updated = mutableListOf<TemporaryTarget>().apply {
        }

        `when`(
            repository.runTransactionForResult(argThatKotlin<InsertAndCancelCurrentTemporaryTargetTransaction> {
                it.temporaryTarget
                    .copy(timestamp = expectedTarget.timestamp, utcOffset = expectedTarget.utcOffset) // those can be different
                    .contentEqualsTo(expectedTarget)
            })
        ).thenReturn(Single.just(InsertAndCancelCurrentTemporaryTargetTransaction.TransactionResult().apply {
            inserted.addAll(inserted)
            updated.addAll(updated)
        }))

        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
        Mockito.verify(repository, Mockito.times(1)).runTransactionForResult(anyObject<Transaction<InsertAndCancelCurrentTemporaryTargetTransaction.TransactionResult>>())
    }

    @Test fun hasDialogTest() {
        Assert.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.value = InputTempTarget(profileFunction)
        sut.value.value = 100.0
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        Assert.assertEquals("{\"data\":{\"durationInMinutes\":30,\"units\":\"mg/dl\",\"value\":100},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionStartTempTarget\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("{\"value\":100,\"durationInMinutes\":30,\"units\":\"mg/dl\"}")
        Assert.assertEquals(GlucoseUnit.MGDL, sut.value.units)
        Assert.assertEquals(100.0, sut.value.value, 0.001)
        Assert.assertEquals(30.0, sut.duration.getMinutes().toDouble(), 0.001)
    }
}