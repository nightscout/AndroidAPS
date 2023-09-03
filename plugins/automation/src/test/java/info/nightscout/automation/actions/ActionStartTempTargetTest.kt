package info.nightscout.automation.actions

import info.nightscout.automation.R
import info.nightscout.automation.elements.InputDuration
import info.nightscout.automation.elements.InputTempTarget
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.impl.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import info.nightscout.database.impl.transactions.Transaction
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.queue.Callback
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionStartTempTargetTest : ActionsTestBase() {

    private lateinit var sut: ActionStartTempTarget

    @BeforeEach
    fun setup() {
        `when`(rh.gs(R.string.starttemptarget)).thenReturn("Start temp target")

        sut = ActionStartTempTarget(injector)
    }

    @Test fun friendlyNameTest() {
        Assertions.assertEquals(R.string.starttemptarget, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.value = InputTempTarget(profileFunction)
        sut.value.value = 100.0
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        Assertions.assertEquals("Start temp target: 100mg/dl@null(Automation)", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assertions.assertEquals(info.nightscout.core.main.R.drawable.ic_temptarget_high, sut.icon())
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
                Assertions.assertTrue(result.success)
            }
        })
        Mockito.verify(repository, Mockito.times(1)).runTransactionForResult(anyObject<Transaction<InsertAndCancelCurrentTemporaryTargetTransaction.TransactionResult>>())
    }

    @Test fun hasDialogTest() {
        Assertions.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.value = InputTempTarget(profileFunction)
        sut.value.value = 100.0
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        Assertions.assertEquals("{\"data\":{\"durationInMinutes\":30,\"units\":\"mg/dl\",\"value\":100},\"type\":\"ActionStartTempTarget\"}", sut.toJSON())
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("{\"value\":100,\"durationInMinutes\":30,\"units\":\"mg/dl\"}")
        Assertions.assertEquals(GlucoseUnit.MGDL, sut.value.units)
        Assertions.assertEquals(100.0, sut.value.value, 0.001)
        Assertions.assertEquals(30.0, sut.duration.getMinutes().toDouble(), 0.001)
    }
}