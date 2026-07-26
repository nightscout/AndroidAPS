package app.aaps.implementation.stats

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Covers [TirImpl]'s Android view builders (`toTableRow` / `toTableRowHeader`), which need a real
 * [android.content.Context] — the counter logic is covered separately by [TirImplTest] (plain JVM).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
internal class TirImplViewTest {

    private val rh: ResourceHelper = mock()
    private val dateUtil: DateUtil = mock()
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        whenever(rh.gs(anyInt())).thenReturn("x")
        whenever(rh.gs(anyInt(), any())).thenReturn("x")
        whenever(dateUtil.dateStringShort(anyLong())).thenReturn("d")
    }

    @Test
    fun `table rows have a date column plus below, in-range and above columns`() {
        val tir = TirImpl(1_600_000_000_000L, 70.0, 180.0).apply { below(); inRange(); above() }

        assertThat(TirImpl.toTableRowHeader(context, rh).childCount).isEqualTo(4)
        assertThat(tir.toTableRow(context, rh, dateUtil).childCount).isEqualTo(4)
        assertThat(tir.toTableRow(context, rh, days = 7).childCount).isEqualTo(4)
    }
}
