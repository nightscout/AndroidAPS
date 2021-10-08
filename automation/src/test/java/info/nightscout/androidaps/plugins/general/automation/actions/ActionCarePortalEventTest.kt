package info.nightscout.androidaps.plugins.general.automation.actions

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.transactions.InsertIfNewByTimestampTherapyEventTransaction
import info.nightscout.androidaps.database.transactions.Transaction
import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.automation.elements.InputCarePortalMenu
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.InputString
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.Completable
import io.reactivex.Single
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.doNothing

class ActionCarePortalEventTest : TestBase() {

    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var sp: SP
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var profileFunction: ProfileFunction

    private lateinit var sut: ActionCarePortalEvent
    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is ActionCarePortalEvent) {
                it.resourceHelper = resourceHelper
                it.repository = repository
                it.sp = sp
                it.dateUtil = dateUtil
                it.profileFunction = profileFunction
            }
        }
    }

    @Before
    fun setup() {
        `when`(sp.getString(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn("AndroidAPS")
        `when`(
            resourceHelper.gs(
                ArgumentMatchers.eq(R.string.careportal_note_message),
                ArgumentMatchers.anyString()
            )
        ).thenReturn("Note : %s")
        `when`(dateUtil.now()).thenReturn(0)
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        `when`(repository.runTransaction(anyObject<Transaction<InsertIfNewByTimestampTherapyEventTransaction.TransactionResult>>()))
            .thenReturn(Completable.fromAction {})
        sut = ActionCarePortalEvent(injector)
        sut.cpEvent = InputCarePortalMenu(resourceHelper)
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
/*
    @Test fun doActionTest() {
        sut.doAction(object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
    }
*/
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