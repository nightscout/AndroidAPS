package app.aaps.plugins.automation.actions

import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class ActionNotificationTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer

    private lateinit var sut: ActionNotification


    @BeforeEach
    fun setup() {
        whenever(rh.gs(CoreUiStrings.notification)).thenReturn("Notification")
        whenever(rh.gs(eq(AutomationStrings.notification_message), any())).thenReturn("Notification: %s")
        runTest {
            whenever(persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), any(), any(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }

        sut = ActionNotification(aapsLogger, rh, Provider { pumpEnactResultProvider.get() }, rxBus, notificationManager, persistenceLayer, dateUtil)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(CoreUiStrings.notification)
    }

    @Test fun shortDescriptionTest() {
        sut.text = InputString("Asd")
        assertThat(sut.shortDescription()).isEqualTo("Notification: Asd")
    }

    @Test fun doActionTest() = runTest {
        val result = sut.doAction()
        assertThat(result.success).isTrue()
    }

    @Test fun hasDialogTest() {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() {
        sut.text = InputString("Asd")
        JSONAssert.assertEquals(
            """{"data":{"text":"Asd"},"type":"ActionNotification"}""",
            sut.toJSON(),
            true,
        )
    }

    @Test fun fromJSONTest() {
        sut.text = InputString("Asd")
        sut.fromJSON("""{"text":"Asd"}""")
        assertThat(sut.text.value).isEqualTo("Asd")
    }
}
