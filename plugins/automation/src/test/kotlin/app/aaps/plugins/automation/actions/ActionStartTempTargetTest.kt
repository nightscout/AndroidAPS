package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputTempTarget
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class ActionStartTempTargetTest : ActionsTestBase() {

    private lateinit var sut: ActionStartTempTarget

    @BeforeEach
    fun setup() {
        whenever(rh.gs(R.string.starttemptarget)).thenReturn("Start temp target")

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
        assertThat(sut.icon()).isEqualTo(app.aaps.core.objects.R.drawable.ic_temptarget_high_24dp)
    }

    @Test fun doActionTest() {

        val expectedTarget = TT(
            id = 0,
            version = 0,
            dateCreated = -1,
            isValid = true,
            referenceId = null,
            ids = IDs(),
            timestamp = 0,
            utcOffset = 0,
            reason = TT.Reason.AUTOMATION,
            highTarget = 110.0,
            lowTarget = 110.0,
            duration = 1800000
        )

        val inserted = mutableListOf<TT>().apply {
            add(expectedTarget)
        }

        val updated = mutableListOf<TT>().apply {
        }

        whenever(
            persistenceLayer.insertAndCancelCurrentTemporaryTarget(argThat {
                copy(timestamp = expectedTarget.timestamp, utcOffset = expectedTarget.utcOffset) // those can be different
                    .contentEqualsTo(expectedTarget)
            }, anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        ).thenReturn(Single.just(PersistenceLayer.TransactionResult<TT>().apply {
            inserted.addAll(inserted)
            updated.addAll(updated)
        }))

        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
        verify(persistenceLayer, times(1)).insertAndCancelCurrentTemporaryTarget(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
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
