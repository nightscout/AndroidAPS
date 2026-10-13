package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class ActionStopTempTargetTest : ActionsTestBase() {

    private lateinit var sut: ActionStopTempTarget

    @BeforeEach
    fun setup() {
        whenever(rh.gs(app.aaps.core.ui.R.string.stoptemptarget)).thenReturn("Stop temp target")

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
        val inserted = mutableListOf<TT>().apply {
            // insert all inserted TTs
        }
        val updated = mutableListOf<TT>().apply {
            // add(TemporaryTarget(id = 0, version = 0, dateCreated = 0, isValid = false, referenceId = null, interfaceIDs_backing = null, timestamp = 0, utcOffset = 0, reason =, highTarget = 0.0, lowTarget = 0.0, duration = 0))
            // insert all updated TTs
        }
        whenever(persistenceLayer.cancelCurrentTemporaryTargetIfAny(any(), any(), any(), any(), any()))
            .thenReturn(Single.just(PersistenceLayer.TransactionResult<TT>().apply {
                inserted.addAll(inserted)
                updated.addAll(updated)
            }))

        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
        verify(persistenceLayer, times(1)).cancelCurrentTemporaryTargetIfAny(any(), any(), any(), any(), any())
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
