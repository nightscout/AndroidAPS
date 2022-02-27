package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.database.transactions.InsertIfNewByTimestampTherapyEventTransaction
import info.nightscout.androidaps.database.transactions.Transaction
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.plugins.general.automation.elements.InputCarePortalMenu
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.InputString
import info.nightscout.androidaps.queue.Callback
import io.reactivex.rxjava3.core.Single
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`

class ActionCarePortalEventTest : ActionsTestBase() {

    private lateinit var sut: ActionCarePortalEvent

    @Before
    fun setup() {
        `when`(sp.getString(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn("AndroidAPS")
        `when`(
            rh.gs(
                ArgumentMatchers.eq(R.string.careportal_note_message),
                ArgumentMatchers.anyString()
            )
        ).thenReturn("Note : %s")
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
        Assert.assertEquals(R.string.careportal, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assert.assertEquals("Note : %s", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.ic_cp_note, sut.icon())
    }

    @Test fun doActionTest() {
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
    }

    @Test fun hasDialogTest() {
        Assert.assertTrue(sut.hasDialog())
    }

    @Test fun toJSONTest() {
        Assert.assertEquals(
            "{\"data\":{\"note\":\"Asd\",\"cpEvent\":\"NOTE\",\"durationInMinutes\":5},\"type\":\"info.nightscout.androidaps.plugins.general.automation.actions.ActionCarePortalEvent\"}",
            sut.toJSON()
        )
    }

    @Test fun fromJSONTest() {
        sut.note = InputString("Asd")
        sut.fromJSON("{\"note\":\"Asd\",\"cpEvent\":\"NOTE\",\"durationInMinutes\":5}")
        Assert.assertEquals("Asd", sut.note.value)
        Assert.assertEquals(5, sut.duration.value)
        Assert.assertEquals(InputCarePortalMenu.EventType.NOTE, sut.cpEvent.value)
    }
}