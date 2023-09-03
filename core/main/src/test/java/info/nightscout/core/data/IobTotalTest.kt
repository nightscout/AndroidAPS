package info.nightscout.core.data

import android.content.Context
import info.nightscout.core.iob.combine
import info.nightscout.core.iob.copy
import info.nightscout.core.iob.determineBasalJson
import info.nightscout.core.iob.json
import info.nightscout.core.iob.plus
import info.nightscout.core.iob.round
import info.nightscout.interfaces.iob.IobTotal
import info.nightscout.shared.utils.DateUtil
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

@Suppress("SpellCheckingInspection")
class IobTotalTest : TestBase() {

    @Mock lateinit var context: Context

    private lateinit var dateUtil: DateUtil
    private var now = 0L

    @BeforeEach
    fun prepare() {
        dateUtil = DateUtil(context)
        now = dateUtil.now()
    }

    @Test fun copyTest() {
        val a = IobTotal(now)
        a.iob = 10.0
        val b = a.copy()
        Assertions.assertEquals(a.iob, b.iob, 0.01)
    }

    @Test fun plusTest() {
        val a = IobTotal(now)
        a.iob = 10.0
        a.activity = 10.0
        a.bolussnooze = 10.0
        a.basaliob = 10.0
        a.netbasalinsulin = 10.0
        a.hightempinsulin = 10.0
        a.netInsulin = 10.0
        a.extendedBolusInsulin = 10.0
        a.plus(a.copy())
        Assertions.assertEquals(20.0, a.iob, 0.01)
        Assertions.assertEquals(20.0, a.activity, 0.01)
        Assertions.assertEquals(20.0, a.bolussnooze, 0.01)
        Assertions.assertEquals(20.0, a.basaliob, 0.01)
        Assertions.assertEquals(20.0, a.netbasalinsulin, 0.01)
        Assertions.assertEquals(20.0, a.hightempinsulin, 0.01)
        Assertions.assertEquals(20.0, a.netInsulin, 0.01)
        Assertions.assertEquals(20.0, a.extendedBolusInsulin, 0.01)
    }

    @Test fun combineTest() {
        val a = IobTotal(now)
        a.iob = 10.0
        a.activity = 11.0
        a.bolussnooze = 12.0
        a.basaliob = 13.0
        a.netbasalinsulin = 14.0
        a.hightempinsulin = 15.0
        a.netInsulin = 16.0
        a.extendedBolusInsulin = 17.0
        val b = a.copy()
        val c = IobTotal.combine(a, b)
        Assertions.assertEquals(a.time.toDouble(), c.time.toDouble(), 0.01)
        Assertions.assertEquals(23.0, c.iob, 0.01)
        Assertions.assertEquals(22.0, c.activity, 0.01)
        Assertions.assertEquals(12.0, c.bolussnooze, 0.01)
        Assertions.assertEquals(26.0, c.basaliob, 0.01)
        Assertions.assertEquals(28.0, c.netbasalinsulin, 0.01)
        Assertions.assertEquals(30.0, c.hightempinsulin, 0.01)
        Assertions.assertEquals(32.0, c.netInsulin, 0.01)
        Assertions.assertEquals(34.0, c.extendedBolusInsulin, 0.01)
    }

    @Test fun roundTest() {
        val a = IobTotal(now)
        a.iob = 1.1111111111111
        a.activity = 1.1111111111111
        a.bolussnooze = 1.1111111111111
        a.basaliob = 1.1111111111111
        a.netbasalinsulin = 1.1111111111111
        a.hightempinsulin = 1.1111111111111
        a.netInsulin = 1.1111111111111
        a.extendedBolusInsulin = 1.1111111111111
        a.round()
        Assertions.assertEquals(1.111, a.iob, 0.00001)
        Assertions.assertEquals(1.1111, a.activity, 0.00001)
        Assertions.assertEquals(1.1111, a.bolussnooze, 0.00001)
        Assertions.assertEquals(1.111, a.basaliob, 0.00001)
        Assertions.assertEquals(1.111, a.netbasalinsulin, 0.00001)
        Assertions.assertEquals(1.111, a.hightempinsulin, 0.00001)
        Assertions.assertEquals(1.111, a.netInsulin, 0.00001)
        Assertions.assertEquals(1.111, a.extendedBolusInsulin, 0.00001)
    }

    @Test fun jsonTest() {
        val a = IobTotal(now)
        a.iob = 10.0
        a.activity = 11.0
        a.bolussnooze = 12.0
        a.basaliob = 13.0
        a.netbasalinsulin = 14.0
        a.hightempinsulin = 15.0
        a.netInsulin = 16.0
        a.extendedBolusInsulin = 17.0
        try {
            val j = a.json(dateUtil)
            Assertions.assertEquals(a.iob, j.getDouble("iob"), 0.0000001)
            Assertions.assertEquals(a.basaliob, j.getDouble("basaliob"), 0.0000001)
            Assertions.assertEquals(a.activity, j.getDouble("activity"), 0.0000001)
            Assertions.assertEquals(now, dateUtil.fromISODateString(j.getString("time")))
        } catch (e: Exception) {
            Assertions.fail("Exception: " + e.message)
        }
    }

    @Test fun determineBasalJsonTest() {
        val a = IobTotal(now)
        a.iob = 10.0
        a.activity = 11.0
        a.bolussnooze = 12.0
        a.basaliob = 13.0
        a.netbasalinsulin = 14.0
        a.hightempinsulin = 15.0
        a.netInsulin = 16.0
        a.extendedBolusInsulin = 17.0
        a.iobWithZeroTemp = IobTotal(now)
        try {
            val j = a.determineBasalJson(dateUtil)
            Assertions.assertEquals(a.iob, j.getDouble("iob"), 0.0000001)
            Assertions.assertEquals(a.basaliob, j.getDouble("basaliob"), 0.0000001)
            Assertions.assertEquals(a.bolussnooze, j.getDouble("bolussnooze"), 0.0000001)
            Assertions.assertEquals(a.activity, j.getDouble("activity"), 0.0000001)
            Assertions.assertEquals(0, j.getLong("lastBolusTime"))
            Assertions.assertEquals(now, dateUtil.fromISODateString(j.getString("time")))
            Assertions.assertNotNull(j.getJSONObject("iobWithZeroTemp"))
        } catch (e: Exception) {
            Assertions.fail("Exception: " + e.message)
        }
    }
}