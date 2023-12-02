package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.queue.Callback
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.impl.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import app.aaps.database.impl.transactions.Transaction
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputTempTarget
import io.reactivex.rxjava3.core.Single
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.skyscreamer.jsonassert.JSONAssert

class ActionStartTempTargetTest : ActionsTestBase() {

    private lateinit var sut: ActionStartTempTarget

    @BeforeEach
    fun setup() {
        `when`(rh.gs(R.string.starttemptarget)).thenReturn("Start temp target")

        sut = ActionStartTempTarget(injector)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(R.string.starttemptarget)
    }

    @Test fun shortDescriptionTest() {
        sut.value = InputTempTarget(profileFunction)
        sut.value.value = 100.0
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        assertThat(sut.shortDescription()).isEqualTo("Start temp target: 100mg/dl@null(Automation)")
    }

    @Test fun iconTest() {
        assertThat(sut.icon()).isEqualTo(app.aaps.core.main.R.drawable.ic_temptarget_high)
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
                assertThat(result.success).isTrue()
            }
        })
        Mockito.verify(repository, Mockito.times(1)).runTransactionForResult(anyObject<Transaction<InsertAndCancelCurrentTemporaryTargetTransaction.TransactionResult>>())
    }

    @Test fun hasDialogTest() {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() {
        sut.value = InputTempTarget(profileFunction)
        sut.value.value = 100.0
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        JSONAssert.assertEquals("""{"data":{"durationInMinutes":30,"units":"mg/dl","value":100},"type":"ActionStartTempTarget"}""", sut.toJSON(), true)
    }

    @Test fun fromJSONTest() {
        sut.fromJSON("""{"value":100,"durationInMinutes":30,"units":"mg/dl"}""")
        assertThat(sut.value.units).isEqualTo(GlucoseUnit.MGDL)
        assertThat(sut.value.value).isWithin(0.001).of(100.0)
        assertThat(sut.duration.getMinutes().toDouble()).isWithin(0.001).of(30.0)
    }
}
