package app.aaps.plugins.aps.extensions

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class PumpEnactResultExtensionTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var decimalFormatter: DecimalFormatter

    // Simple test implementation of PumpEnactResult
    private class TestPumpEnactResult : PumpEnactResult {
        override var success = false
        override var enacted = false
        override var comment = ""
        override var duration = -1
        override var absolute = -1.0
        override var percent = -1
        override var isPercent = false
        override var isTempCancel = false
        override var bolusDelivered = 0.0
        override var queued = false

        override fun success(success: Boolean) = apply { this.success = success }
        override fun enacted(enacted: Boolean) = apply { this.enacted = enacted }
        override fun comment(comment: String) = apply { this.comment = comment }
        override fun comment(comment: Int) = apply { this.comment = comment.toString() }
        override fun duration(duration: Int) = apply { this.duration = duration }
        override fun absolute(absolute: Double) = apply { this.absolute = absolute }
        override fun percent(percent: Int) = apply { this.percent = percent }
        override fun isPercent(isPercent: Boolean) = apply { this.isPercent = isPercent }
        override fun isTempCancel(isTempCancel: Boolean) = apply { this.isTempCancel = isTempCancel }
        override fun bolusDelivered(bolusDelivered: Double) = apply { this.bolusDelivered = bolusDelivered }
        override fun queued(queued: Boolean) = apply { this.queued = queued }
    }

    @BeforeEach
    fun setup() {
        // Setup common resource string mocks
        whenever(rh.gs(app.aaps.core.ui.R.string.success)).thenReturn("Success")
        whenever(rh.gs(app.aaps.core.ui.R.string.enacted)).thenReturn("Enacted")
        whenever(rh.gs(app.aaps.core.ui.R.string.comment)).thenReturn("Comment")
        whenever(rh.gs(app.aaps.core.ui.R.string.duration)).thenReturn("Duration")
        whenever(rh.gs(app.aaps.core.ui.R.string.percent)).thenReturn("Percent")
        whenever(rh.gs(app.aaps.core.ui.R.string.absolute)).thenReturn("Absolute")
        whenever(rh.gs(app.aaps.core.ui.R.string.waitingforpumpresult)).thenReturn("Waiting for pump result")
        whenever(rh.gs(app.aaps.core.ui.R.string.smb_shortname)).thenReturn("SMB")
        whenever(rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)).thenReturn("U")
        whenever(rh.gs(app.aaps.core.ui.R.string.cancel_temp)).thenReturn("Cancel temp basal")
        whenever(decimalFormatter.to2Decimal(any())).thenAnswer {
            String.format("%.2f", it.arguments[0] as Double)
        }
    }

    @Test
    fun `toHtml with queued result shows waiting message`() {
        val result = TestPumpEnactResult().apply {
            success = true
            queued = true
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).isEqualTo("Waiting for pump result")
    }

    @Test
    fun `toHtml with simple success shows success status`() {
        val result = TestPumpEnactResult().apply {
            success = true
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).contains("<b>Success</b>: true")
    }

    @Test
    fun `toHtml with simple failure shows success status`() {
        val result = TestPumpEnactResult().apply {
            success = false
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).contains("<b>Success</b>: false")
    }

    @Test
    fun `toHtml with bolus delivered shows bolus information`() {
        val result = TestPumpEnactResult().apply {
            success = true
            enacted = true
            bolusDelivered = 5.0
            comment = "Meal bolus"
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).contains("<b>Success</b>: true")
        assertThat(html).contains("<b>Enacted</b>: true")
        assertThat(html).contains("<b>Comment</b>: Meal bolus")
        assertThat(html).contains("<b>SMB</b>: 5.0 U")
    }

    @Test
    fun `toHtml with temp basal cancel shows cancel message`() {
        val result = TestPumpEnactResult().apply {
            success = true
            enacted = true
            isTempCancel = true
            comment = "Cancelled by user"
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).contains("<b>Enacted</b>: true")
        assertThat(html).contains("<b>Comment</b>: Cancelled by user")
        assertThat(html).contains("Cancel temp basal")
    }

    @Test
    fun `toHtml with percent temp basal shows percent information`() {
        val result = TestPumpEnactResult().apply {
            success = true
            enacted = true
            isPercent = true
            percent = 120
            duration = 30
            comment = "High temp"
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).contains("<b>Enacted</b>: true")
        assertThat(html).contains("<b>Comment</b>: High temp")
        assertThat(html).contains("<b>Duration</b>: 30 min")
        assertThat(html).contains("<b>Percent</b>: 120%")
    }

    @Test
    fun `toHtml with absolute temp basal shows absolute information`() {
        val result = TestPumpEnactResult().apply {
            success = true
            enacted = true
            absolute = 1.5
            duration = 30
            comment = "Moderate temp"
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).contains("<b>Enacted</b>: true")
        assertThat(html).contains("<b>Comment</b>: Moderate temp")
        assertThat(html).contains("<b>Duration</b>: 30 min")
        assertThat(html).contains("<b>Absolute</b>: 1.50 U/h")
    }

    @Test
    fun `toHtml with not enacted but with comment shows comment`() {
        val result = TestPumpEnactResult().apply {
            success = false
            enacted = false
            comment = "Pump not reachable"
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).contains("<b>Success</b>: false")
        assertThat(html).contains("<b>Comment</b>: Pump not reachable")
    }

    @Test
    fun `toHtml without comment does not include comment line`() {
        val result = TestPumpEnactResult().apply {
            success = true
            enacted = true
            isPercent = true
            percent = 100
            duration = 30
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).doesNotContain("<b>Comment</b>:")
    }

    @Test
    fun `toHtml with bolus and empty comment does not include comment line`() {
        val result = TestPumpEnactResult().apply {
            success = true
            enacted = true
            bolusDelivered = 3.0
            comment = ""
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).contains("<b>SMB</b>: 3.0 U")
        assertThat(html).doesNotContain("<b>Comment</b>:")
    }

    @Test
    fun `toHtml with absolute value of -1 is not shown`() {
        val result = TestPumpEnactResult().apply {
            success = true
            enacted = true
            absolute = -1.0
            comment = "Some action"
        }

        val html = result.toHtml(rh, decimalFormatter)

        // When enacted=true but no when case matches, comment is not shown
        assertThat(html).isEqualTo("<b>Success</b>: true")
        assertThat(html).doesNotContain("<b>Absolute</b>:")
        assertThat(html).doesNotContain("<b>Comment</b>:")
    }

    @Test
    fun `toHtml with percent -1 is not shown`() {
        val result = TestPumpEnactResult().apply {
            success = true
            enacted = true
            isPercent = true
            percent = -1
            comment = "Some action"
        }

        val html = result.toHtml(rh, decimalFormatter)

        // When enacted=true but no when case matches, comment is not shown
        assertThat(html).isEqualTo("<b>Success</b>: true")
        assertThat(html).doesNotContain("<b>Percent</b>:")
        assertThat(html).doesNotContain("<b>Comment</b>:")
    }

    @Test
    fun `toHtml with zero bolus delivered shows bolus`() {
        val result = TestPumpEnactResult().apply {
            success = true
            enacted = true
            bolusDelivered = 0.0
        }

        val html = result.toHtml(rh, decimalFormatter)

        // bolusDelivered > 0 check should exclude this
        assertThat(html).doesNotContain("<b>SMB</b>:")
    }

    @Test
    fun `toHtml with very small bolus shows bolus`() {
        val result = TestPumpEnactResult().apply {
            success = true
            enacted = true
            bolusDelivered = 0.1
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).contains("<b>SMB</b>: 0.1 U")
    }

    @Test
    fun `toHtml formats absolute value to 2 decimals`() {
        val result = TestPumpEnactResult().apply {
            success = true
            enacted = true
            absolute = 2.456
            duration = 30
        }

        val html = result.toHtml(rh, decimalFormatter)

        assertThat(html).contains("<b>Absolute</b>: 2.46 U/h")
    }
}
