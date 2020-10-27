package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.plugins.general.automation.elements.ComparatorExists
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(DateUtil::class, TreatmentsPlugin::class)
class TriggerTempTargetTest : TriggerTestBase() {

    var now = 1514766900000L

    @Before fun mock() {
        PowerMockito.mockStatic(DateUtil::class.java)
        PowerMockito.`when`(DateUtil.now()).thenReturn(now)
    }

    @Test fun shouldRunTest() {
        `when`(treatmentsPlugin.tempTargetFromHistory).thenReturn(null)
        var t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
        Assert.assertFalse(t.shouldRun())
        t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
        Assert.assertTrue(t.shouldRun())
        PowerMockito.`when`(treatmentsPlugin.tempTargetFromHistory).thenReturn(TempTarget())
        t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
        Assert.assertFalse(t.shouldRun())
        t = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
        Assert.assertTrue(t.shouldRun())
    }

    @Test fun copyConstructorTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
        val t1 = t.duplicate() as TriggerTempTarget
        Assert.assertEquals(ComparatorExists.Compare.NOT_EXISTS, t1.comparator.value)
    }

    private var ttJson = "{\"data\":{\"comparator\":\"EXISTS\"},\"type\":\"info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTempTarget\"}"
    @Test fun toJSONTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.EXISTS)
        Assert.assertEquals(ttJson, t.toJSON())
    }

    @Test
    fun fromJSONTest() {
        val t: TriggerTempTarget = TriggerTempTarget(injector).comparator(ComparatorExists.Compare.NOT_EXISTS)
        val t2 = TriggerDummy(injector).instantiate(JSONObject(t.toJSON())) as TriggerTempTarget
        Assert.assertEquals(ComparatorExists.Compare.NOT_EXISTS, t2.comparator.value)
    }

    @Test fun iconTest() {
        Assert.assertEquals(Optional.of(R.drawable.ic_keyboard_tab), TriggerTempTarget(injector).icon())
    }
}