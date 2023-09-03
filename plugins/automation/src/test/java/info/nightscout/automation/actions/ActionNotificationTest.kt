package info.nightscout.automation.actions

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.automation.elements.InputString
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.database.impl.transactions.Transaction
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.sharedtests.TestBase
import io.reactivex.rxjava3.core.Completable
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionNotificationTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var repository: AppRepository

    private lateinit var sut: ActionNotification
    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActionNotification) {
                it.rh = rh
                it.rxBus = rxBus
                it.repository = repository
            }
            if (it is PumpEnactResult) {
                it.context = context
            }
        }
    }

    @BeforeEach
    fun setup() {
        `when`(context.getString(info.nightscout.core.ui.R.string.ok)).thenReturn("OK")
        `when`(rh.gs(info.nightscout.core.ui.R.string.notification)).thenReturn("Notification")
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
        Assertions.assertEquals(info.nightscout.core.ui.R.string.notification, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.text = InputString("Asd")
        Assertions.assertEquals("Notification: %s", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assertions.assertEquals(R.drawable.ic_notifications, sut.icon())
    }

    @Test fun doActionTest() {
        sut.doAction(object : Callback() {
            override fun run() {
                Assertions.assertTrue(result.success)
            }
        })
        Mockito.verify(rxBus, Mockito.times(2)).send(anyObject())
        //Mockito.verify(repository, Mockito.times(1)).runTransaction(any(Transaction::class.java))
    }

    @Test fun hasDialogTest() {
        Assertions.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        sut.text = InputString("Asd")
        Assertions.assertEquals(
            "{\"data\":{\"text\":\"Asd\"},\"type\":\"ActionNotification\"}",
            sut.toJSON()
        )
    }

    @Test fun fromJSONTest() {
        sut.text = InputString("Asd")
        sut.fromJSON("{\"text\":\"Asd\"}")
        Assertions.assertEquals("Asd", sut.text.value)
    }
}