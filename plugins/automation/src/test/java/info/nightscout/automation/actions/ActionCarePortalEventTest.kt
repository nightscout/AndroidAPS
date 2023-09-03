package info.nightscout.automation.actions

import info.nightscout.automation.elements.InputCarePortalMenu
import info.nightscout.automation.elements.InputDuration
import info.nightscout.automation.elements.InputString
import info.nightscout.database.impl.transactions.InsertIfNewByTimestampTherapyEventTransaction
import info.nightscout.database.impl.transactions.Transaction
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.queue.Callback
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`

class ActionCarePortalEventTest : ActionsTestBase() {

    private lateinit var sut: ActionCarePortalEvent

    @BeforeEach
    fun setup() {
        `when`(sp.getString(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn("AAPS")
        `when`(rh.gs(info.nightscout.core.ui.R.string.careportal_note_message)).thenReturn("Note : %s")
        `when`(dateUtil.now()).thenReturn(0)
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        `when`(repository.runTransactionForResult(anyObject<Transaction<InsertIfNewByTimestampTherapyEventTransaction.TransactionResult>>()))
            .thenReturn(Single.just(InsertIfNewByTimestampTherapyEventTransaction.TransactionResult().apply {
            }))
        sut = ActionCarePortalEvent(injector)
        sut.cpEvent = InputCarePortalMenu(rh)
        sut.cpEvent.value = InputCarePortalMenu.EventType.NOTE
        sut.note = InputString("Asd")
        sut.duration = InputDuration(5, InputDuration.TimeUnit.MINUTES)
    }

    @Test fun friendlyNameTest() {
        Assertions.assertEquals(info.nightscout.core.ui.R.string.careportal, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assertions.assertEquals("Note : Asd", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assertions.assertEquals(info.nightscout.core.main.R.drawable.ic_cp_note, sut.icon())
    }

    @Test fun doActionTest() {
        sut.doAction(object : Callback() {
            override fun run() {
                Assertions.assertTrue(result.success)
            }
        })
    }

    @Test fun hasDialogTest() {
        Assertions.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        Assertions.assertEquals(
            "{\"data\":{\"note\":\"Asd\",\"cpEvent\":\"NOTE\",\"durationInMinutes\":5},\"type\":\"ActionCarePortalEvent\"}",
            sut.toJSON()
        )
    }

    @Test fun fromJSONTest() {
        sut.note = InputString("Asd")
        sut.fromJSON("{\"note\":\"Asd\",\"cpEvent\":\"NOTE\",\"durationInMinutes\":5}")
        Assertions.assertEquals("Asd", sut.note.value)
        Assertions.assertEquals(5, sut.duration.value)
        Assertions.assertEquals(InputCarePortalMenu.EventType.NOTE, sut.cpEvent.value)
    }
}