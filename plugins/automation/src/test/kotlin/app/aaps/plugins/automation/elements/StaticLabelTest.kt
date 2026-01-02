package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerDummy
import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class StaticLabelTest : TriggerTestBase() {

    @Test fun constructor() {
        var sl = StaticLabel(rh, "any", TriggerDummy(injector))
        assertThat(sl.label).isEqualTo("any")
        whenever(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        sl = StaticLabel(rh, app.aaps.core.ui.R.string.pumplimit, TriggerDummy(injector))
        assertThat(sl.label).isEqualTo("pump limit")
    }
}
