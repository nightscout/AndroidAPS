package app.aaps.plugins.automation.actions

import android.content.Context
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.InsertTherapyEventAnnouncementTransaction
import app.aaps.database.impl.transactions.Transaction
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import app.aaps.shared.tests.TestBase
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.core.Completable
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.skyscreamer.jsonassert.JSONAssert

class ActionNotificationTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var rxBusMocked: RxBus
    @Mock lateinit var repository: AppRepository

    private lateinit var sut: ActionNotification
    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActionNotification) {
                it.rh = rh
                it.rxBus = rxBusMocked
                it.repository = repository
            }
            if (it is PumpEnactResult) {
                it.context = context
            }
        }
    }

    @BeforeEach
    fun setup() {
        `when`(context.getString(app.aaps.core.ui.R.string.ok)).thenReturn("OK")
        `when`(rh.gs(app.aaps.core.ui.R.string.notification)).thenReturn("Notification")
        `when`(
            rh.gs(
                ArgumentMatchers.eq(R.string.notification_message),
                ArgumentMatchers.anyString()
            )
        ).thenReturn("Notification: %s")
        `when`(repository.runTransaction(anyObject<Transaction<InsertTherapyEventAnnouncementTransaction.TransactionResult>>()))
            .thenReturn(Completable.fromAction {})

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
        Mockito.verify(rxBusMocked, Mockito.times(2)).send(anyObject())
        //Mockito.verify(repository, Mockito.times(1)).runTransaction(any(Transaction::class.java))
    }

    @Test fun hasDialogTest() {
        assertThat(sut.hasDialog())
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
