package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.elements.InputCarePortalMenu
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputString
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.`when`
import org.skyscreamer.jsonassert.JSONAssert

class ActionCarePortalEventTest : ActionsTestBase() {

    private lateinit var sut: ActionCarePortalEvent

    @BeforeEach
    fun setup() {
        `when`(rh.gs(app.aaps.core.ui.R.string.careportal_note_message)).thenReturn("Note : %s")
        `when`(dateUtil.now()).thenReturn(0)
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        `when`(persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(anyObject(), anyLong(), anyObject(), anyObject(), anyObject(), anyObject()))
            .thenReturn(Single.just(PersistenceLayer.TransactionResult<TE>().apply {
            }))
        sut = ActionCarePortalEvent(injector)
        sut.cpEvent = InputCarePortalMenu(rh)
        sut.cpEvent.value = InputCarePortalMenu.EventType.NOTE
        sut.note = InputString("Asd")
        sut.duration = InputDuration(5, InputDuration.TimeUnit.MINUTES)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(app.aaps.core.ui.R.string.careportal)
    }

    @Test fun shortDescriptionTest() {
        assertThat(sut.shortDescription()).isEqualTo("Note : Asd")
    }

    @Test fun iconTest() {
        assertThat(sut.icon()).isEqualTo(app.aaps.core.objects.R.drawable.ic_cp_note_24dp)
    }

    @Test fun doActionTest() {
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
    }

    @Test fun hasDialogTest() {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() {
        JSONAssert.assertEquals(
            """{"data":{"note":"Asd","cpEvent":"NOTE","durationInMinutes":5},"type":"ActionCarePortalEvent"}""",
            sut.toJSON(),
            true,
        )
    }

    @Test fun fromJSONTest() {
        sut.note = InputString("Asd")
        sut.fromJSON("""{"note":"Asd","cpEvent":"NOTE","durationInMinutes":5}""")
        assertThat(sut.note.value).isEqualTo("Asd")
        assertThat(sut.duration.value).isEqualTo(5)
        assertThat(sut.cpEvent.value).isEqualTo(InputCarePortalMenu.EventType.NOTE)
    }
}
