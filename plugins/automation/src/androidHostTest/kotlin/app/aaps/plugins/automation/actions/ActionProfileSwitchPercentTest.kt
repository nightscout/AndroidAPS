package app.aaps.plugins.automation.actions

import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.data.model.PS
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputPercent
import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert

class ActionProfileSwitchPercentTest : ActionsTestBase() {

    private lateinit var sut: ActionProfileSwitchPercent

    @BeforeEach
    fun setup() {

        whenever(rh.gs(AutomationStrings.startprofileforever)).thenReturn("Start profile %d%%")
        whenever(rh.gs(CoreUiStrings.startprofile)).thenReturn("Start profile %d%% for %d min")

        sut = ActionProfileSwitchPercent(aapsLogger, rh, Provider { pumpEnactResultProvider.get() }, profileFunction, triggerDeps)
    }

    @Test fun friendlyNameTest() = runTest {
        assertThat(sut.friendlyName()).isEqualTo(AutomationStrings.profilepercentage)
    }

    @Test fun shortDescriptionTest() = runTest {
        sut.pct = InputPercent(100.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        assertThat(sut.shortDescription()).isEqualTo("Start profile 100% for 30 min")
    }

    @Test fun doActionTest() = runTest {
        whenever(profileFunction.createProfileSwitch(any(), any(), any(), any(), any(), any(), any())).thenReturn(mock<PS>())
        sut.pct = InputPercent(110.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        val result = sut.doAction()
        assertThat(result.success).isTrue()
        verify(profileFunction, times(1)).createProfileSwitch(eq(30), eq(110), eq(0), any(), any(), any(), any())
    }

    @Test fun hasDialogTest() = runTest {
        assertThat(sut.hasDialog()).isTrue()
    }

    @Test fun toJSONTest() = runTest {
        sut.pct = InputPercent(100.0)
        sut.duration = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        JSONAssert.assertEquals("""{"data":{"percentage":100,"durationInMinutes":30},"type":"ActionProfileSwitchPercent"}""", sut.toJSON(), true)
    }

    @Test fun fromJSONTest() = runTest {
        sut.fromJSON("""{"percentage":100,"durationInMinutes":30}""")
        assertThat(sut.pct.value).isWithin(0.001).of(100.0)
        assertThat(sut.duration.getMinutes().toDouble()).isWithin(0.001).of(30.0)
    }
}
