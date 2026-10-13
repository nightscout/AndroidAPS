package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
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

    init {
        addInjector {
            if (it is ActionNotification) {
                it.rh = rh
                it.rxBus = rxBus
                it.persistenceLayer = persistenceLayer
                it.dateUtil = dateUtil
                it.pumpEnactResultProvider = pumpEnactResultProvider
            }
        }
    }

    @BeforeEach
    fun setup() {
        whenever(rh.gs(app.aaps.core.ui.R.string.notification)).thenReturn("Notification")
        whenever(rh.gs(eq(R.string.notification_message), any())).thenReturn("Notification: %s")
        whenever(persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), any(), any(), any()))
            .thenReturn(Single.just(PersistenceLayer.TransactionResult()))

        sut = ActionNotification(injector)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(app.aaps.core.ui.R.string.notification)
    }

    @Test fun shortDescriptionTest() {
        sut.text = InputString("Asd")
        assertThat(sut.shortDescription()).isEqualTo("Notification: %s")
    }

    @Test fun iconTest() {
        assertThat(sut.icon()).isEqualTo(R.drawable.ic_notifications)
    }

    @Test fun doActionTest() {
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
        //verify(rxBus, times(2)).send(anyOrNull())
        //verify(repository, times(1)).runTransaction(any(Transaction::class.java))
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
