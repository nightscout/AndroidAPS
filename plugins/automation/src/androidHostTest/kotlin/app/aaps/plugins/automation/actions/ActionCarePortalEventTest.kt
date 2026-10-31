package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.automation.elements.InputCarePortalMenu
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputString
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class ActionCarePortalEventTest : ActionsTestBase() {

    private lateinit var sut: ActionCarePortalEvent

    @BeforeEach
    fun setup() {
        whenever(dateUtil.now()).thenReturn(0)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        runTest {
            whenever(persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(anyOrNull(), anyLong(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        sut = ActionCarePortalEvent(aapsLogger, text, { pumpEnactResultProvider() }, persistenceLayer, profileFunction, dateUtil, glucoseStatusProvider)
        sut.cpEvent = InputCarePortalMenu()
        sut.cpEvent.value = InputCarePortalMenu.EventType.NOTE
        sut.note = InputString("Asd")
        sut.duration = InputDuration(5, InputDuration.TimeUnit.MINUTES)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(CoreUiStrings.careportal)
    }

    @Test fun shortDescriptionTest() {
        assertThat(sut.shortDescription()).isEqualTo("Note : Asd")
    }

    @Test fun doActionTest() = runTest {
        val result = sut.doAction()
        assertThat(result.success).isTrue()
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

    /** A rule can record a pump site change, for example after the driver recorded a reservoir change (#4548). */
    @Test fun cannulaChangeIsStoredAsPumpSiteChange() = runTest {
        sut.cpEvent.value = InputCarePortalMenu.EventType.CANNULA_CHANGE

        assertThat(sut.doAction().success).isTrue()

        val stored = argumentCaptor<TE>()
        verify(persistenceLayer).insertPumpTherapyEventIfNewByTimestamp(stored.capture(), anyLong(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        assertThat(stored.firstValue.type).isEqualTo(TE.Type.CANNULA_CHANGE)
        assertThat(sut.elementType()).isEqualTo(ElementType.CANNULA_CHANGE)
    }

    @Test fun cannulaChangeSurvivesJsonRoundTrip() {
        sut.cpEvent.value = InputCarePortalMenu.EventType.CANNULA_CHANGE
        val restored = ActionCarePortalEvent(aapsLogger, text, { pumpEnactResultProvider() }, persistenceLayer, profileFunction, dateUtil, glucoseStatusProvider)

        restored.fromJSON(Json.parseToJsonElement(sut.toJSON()).jsonObject["data"].toString())

        assertThat(restored.cpEvent.value).isEqualTo(InputCarePortalMenu.EventType.CANNULA_CHANGE)
    }
}
