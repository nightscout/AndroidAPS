package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.PS
import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputProfileName
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

private const val STRING_JSON = """{"data":{"profileToSwitchTo":"Test"},"type":"ActionProfileSwitch"}"""

class ActionProfileSwitchTest : ActionsTestBase() {

    private val iCfg = ICfg(insulinLabel = "Fake", insulinEndTime = 9 * 3600 * 1000, insulinPeakTime = 60 * 60 * 1000, concentration = 1.0)
    private lateinit var sut: ActionProfileSwitch

    @BeforeEach fun setUp() {
        whenever(rh.gs(R.string.profilename)).thenReturn("Change profile to")
        whenever(rh.gs(R.string.changengetoprofilename)).thenReturn("Change profile to %s")
        whenever(rh.gs(R.string.alreadyset)).thenReturn("Already set")
        whenever(rh.gs(app.aaps.core.ui.R.string.notexists)).thenReturn("not exists")
        whenever(rh.gs(app.aaps.core.ui.R.string.error_field_must_not_be_empty)).thenReturn("The field must not be empty")
        whenever(rh.gs(app.aaps.core.ui.R.string.noprofile)).thenReturn("No profile loaded from NS yet")
        whenever(insulin.iCfg).thenReturn(iCfg)

        sut = ActionProfileSwitch(injector)
    }

    @Test fun friendlyName() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(R.string.profilename)
    }

    @Test fun shortDescriptionTest() = runTest {
        assertThat(sut.shortDescription()).isEqualTo("Change profile to ")
    }

    @Test fun doAction() = runTest {
        //Empty input
        whenever(profileFunction.getProfileName()).thenReturn("Test")
        sut.inputProfileName = InputProfileName(rh, localProfileManager, "")
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isFalse()
            }
        })

        //Not initialized profileStore
        whenever(profileFunction.getProfile()).thenReturn(null)
        sut.inputProfileName = InputProfileName(rh, localProfileManager, "someProfile")
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isFalse()
            }
        })

        //profile already set
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        whenever(profileFunction.getProfileName()).thenReturn("Test")
        sut.inputProfileName = InputProfileName(rh, localProfileManager, "Test")
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
                assertThat(result.comment).isEqualTo("Already set")
            }
        })

        // profile doesn't exists
        whenever(profileFunction.getProfileName()).thenReturn("Active")
        sut.inputProfileName = InputProfileName(rh, localProfileManager, "Test")
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isFalse()
                assertThat(result.comment).isEqualTo("not exists")
            }
        })

        // do profile switch
        whenever(profileFunction.getProfileName()).thenReturn("Test")
        whenever(profileFunction.createProfileSwitch(anyOrNull(), anyString(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any(), any(), any())).thenReturn(mock<PS>())
        sut.inputProfileName = InputProfileName(rh, localProfileManager, TESTPROFILENAME)
        sut.doAction(object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
                assertThat(result.comment).isEqualTo("OK")
            }
        })
        verify(profileFunction, times(1)).createProfileSwitch(anyOrNull(), anyString(), anyInt(), anyInt(), anyInt(), anyLong(), any(), any(), any(), any(), any())
    }

    @Test fun hasDialogTest() = runTest {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() = runTest {
        sut.inputProfileName = InputProfileName(rh, localProfileManager, "Test")
        JSONAssert.assertEquals(STRING_JSON, sut.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        sut.fromJSON("""{"profileToSwitchTo":"Test"}""")
        assertThat(sut.inputProfileName.value).isEqualTo("Test")
    }

    @Test fun iconTest() = runTest {
        assertThat(sut.icon()).isEqualTo(app.aaps.core.ui.R.drawable.ic_actions_profileswitch_24dp)
    }
}
