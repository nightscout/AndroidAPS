package app.aaps.core.interfaces.iob

import android.content.Context
import app.aaps.core.main.iob.combine
import app.aaps.core.main.iob.copy
import app.aaps.core.main.iob.determineBasalJson
import app.aaps.core.main.iob.json
import app.aaps.core.main.iob.plus
import app.aaps.core.main.iob.round
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
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
        dateUtil = DateUtilImpl(context)
        now = dateUtil.now()
    }

    @Test fun copyTest() {
        val a = IobTotal(now)
        a.iob = 10.0
        val b = a.copy()
        assertThat(b.iob).isWithin(0.01).of(a.iob)
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
        assertThat(a.iob).isWithin(0.01).of(20.0)
        assertThat(a.activity).isWithin(0.01).of(20.0)
        assertThat(a.bolussnooze).isWithin(0.01).of(20.0)
        assertThat(a.basaliob).isWithin(0.01).of(20.0)
        assertThat(a.netbasalinsulin).isWithin(0.01).of(20.0)
        assertThat(a.hightempinsulin).isWithin(0.01).of(20.0)
        assertThat(a.netInsulin).isWithin(0.01).of(20.0)
        assertThat(a.extendedBolusInsulin).isWithin(0.01).of(20.0)
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
        assertThat(c.time.toDouble()).isWithin(0.01).of(a.time.toDouble())
        assertThat(c.iob).isWithin(0.01).of(23.0)
        assertThat(c.activity).isWithin(0.01).of(22.0)
        assertThat(c.bolussnooze).isWithin(0.01).of(12.0)
        assertThat(c.basaliob).isWithin(0.01).of(26.0)
        assertThat(c.netbasalinsulin).isWithin(0.01).of(28.0)
        assertThat(c.hightempinsulin).isWithin(0.01).of(30.0)
        assertThat(c.netInsulin).isWithin(0.01).of(32.0)
        assertThat(c.extendedBolusInsulin).isWithin(0.01).of(34.0)
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
        assertThat(a.iob).isWithin(0.00001).of(1.111)
        assertThat(a.activity).isWithin(0.00001).of(1.1111)
        assertThat(a.bolussnooze).isWithin(0.00001).of(1.1111)
        assertThat(a.basaliob).isWithin(0.00001).of(1.111)
        assertThat(a.netbasalinsulin).isWithin(0.00001).of(1.111)
        assertThat(a.hightempinsulin).isWithin(0.00001).of(1.111)
        assertThat(a.netInsulin).isWithin(0.00001).of(1.111)
        assertThat(a.extendedBolusInsulin).isWithin(0.00001).of(1.111)
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
        val j = a.json(dateUtil)
        assertThat(j.getDouble("iob")).isWithin(0.0000001).of(a.iob)
        assertThat(j.getDouble("basaliob")).isWithin(0.0000001).of(a.basaliob)
        assertThat(j.getDouble("activity")).isWithin(0.0000001).of(a.activity)
        assertThat(dateUtil.fromISODateString(j.getString("time"))).isEqualTo(now)
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
        val j = a.determineBasalJson(dateUtil)
        assertThat(j.getDouble("iob")).isWithin(0.0000001).of(a.iob)
        assertThat(j.getDouble("basaliob")).isWithin(0.0000001).of(a.basaliob)
        assertThat(j.getDouble("bolussnooze")).isWithin(0.0000001).of(a.bolussnooze)
        assertThat(j.getDouble("activity")).isWithin(0.0000001).of(a.activity)
        assertThat(j.getLong("lastBolusTime")).isEqualTo(0)
        assertThat(dateUtil.fromISODateString(j.getString("time"))).isEqualTo(now)
        assertThat(j.getJSONObject("iobWithZeroTemp")).isNotNull()
    }
}
