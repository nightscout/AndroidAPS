package app.aaps.plugins.aps.loop.extensions

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PumpEnactResultExtensionTest : TestBase() {

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

    @Test
    fun `json with bolus delivered includes smb field`() {
        val result = TestPumpEnactResult().apply {
            bolusDelivered = 2.5
        }

        val json = result.json(baseBasal = 1.0)

        assertThat(json.getDouble("smb")).isEqualTo(2.5)
        assertThat(json.has("rate")).isFalse()
        assertThat(json.has("duration")).isFalse()
    }

    @Test
    fun `json with zero bolus does not include smb field`() {
        val result = TestPumpEnactResult().apply {
            bolusDelivered = 0.0
        }

        val json = result.json(baseBasal = 1.0)

        assertThat(json.has("smb")).isFalse()
    }

    @Test
    fun `json with temp cancel includes zero rate and duration`() {
        val result = TestPumpEnactResult().apply {
            isTempCancel = true
        }

        val json = result.json(baseBasal = 1.0)

        assertThat(json.getInt("rate")).isEqualTo(0)
        assertThat(json.getInt("duration")).isEqualTo(0)
        assertThat(json.has("smb")).isFalse()
    }

    @Test
    fun `json with percent temp converts to absolute value`() {
        val result = TestPumpEnactResult().apply {
            isPercent = true
            percent = 150
            duration = 30
        }

        val json = result.json(baseBasal = 1.0)

        // 150% of 1.0 = 1.5
        assertThat(json.getDouble("rate")).isEqualTo(1.5)
        assertThat(json.getInt("duration")).isEqualTo(30)
        assertThat(json.has("smb")).isFalse()
    }

    @Test
    fun `json with percent temp rounds to 2 decimals`() {
        val result = TestPumpEnactResult().apply {
            isPercent = true
            percent = 133
            duration = 30
        }

        val json = result.json(baseBasal = 0.9)

        // 133% of 0.9 = 1.197, rounded to 1.2
        assertThat(json.getDouble("rate")).isEqualTo(1.2)
        assertThat(json.getInt("duration")).isEqualTo(30)
    }

    @Test
    fun `json with percent temp handles zero percent`() {
        val result = TestPumpEnactResult().apply {
            isPercent = true
            percent = 0
            duration = 30
        }

        val json = result.json(baseBasal = 1.5)

        // 0% of 1.5 = 0.0
        assertThat(json.getDouble("rate")).isEqualTo(0.0)
        assertThat(json.getInt("duration")).isEqualTo(30)
    }

    @Test
    fun `json with absolute temp includes rate and duration`() {
        val result = TestPumpEnactResult().apply {
            absolute = 2.0
            duration = 45
        }

        val json = result.json(baseBasal = 1.0)

        // Absolute temp puts value in "rate" field, not "absolute"
        assertThat(json.getDouble("rate")).isEqualTo(2.0)
        assertThat(json.getInt("duration")).isEqualTo(45)
        assertThat(json.has("smb")).isFalse()
    }

    @Test
    fun `json with absolute temp does not depend on baseBasal`() {
        val result = TestPumpEnactResult().apply {
            absolute = 2.5
            duration = 30
        }

        // baseBasal should not affect absolute temp
        val json1 = result.json(baseBasal = 1.0)
        val json2 = result.json(baseBasal = 2.0)

        // Absolute temp puts value in "rate" field, not "absolute"
        assertThat(json1.getDouble("rate")).isEqualTo(2.5)
        assertThat(json2.getDouble("rate")).isEqualTo(2.5)
    }

    @Test
    fun `json with percent temp depends on baseBasal`() {
        val result = TestPumpEnactResult().apply {
            isPercent = true
            percent = 200
            duration = 30
        }

        val json1 = result.json(baseBasal = 1.0)
        val json2 = result.json(baseBasal = 2.0)

        // 200% of 1.0 = 2.0
        assertThat(json1.getDouble("rate")).isEqualTo(2.0)
        // 200% of 2.0 = 4.0
        assertThat(json2.getDouble("rate")).isEqualTo(4.0)
    }

    @Test
    fun `json with bolus takes precedence over temp`() {
        val result = TestPumpEnactResult().apply {
            bolusDelivered = 3.0
            absolute = 2.0
            duration = 30
        }

        val json = result.json(baseBasal = 1.0)

        // Should have smb, not rate/duration
        assertThat(json.getDouble("smb")).isEqualTo(3.0)
        assertThat(json.has("rate")).isFalse()
        assertThat(json.has("duration")).isFalse()
    }

    @Test
    fun `json with temp cancel takes precedence over percent`() {
        val result = TestPumpEnactResult().apply {
            isTempCancel = true
            isPercent = true
            percent = 150
            duration = 30
        }

        val json = result.json(baseBasal = 1.0)

        // Should have zero rate and duration, not calculated percent
        assertThat(json.getInt("rate")).isEqualTo(0)
        assertThat(json.getInt("duration")).isEqualTo(0)
    }

    @Test
    fun `json with small bolus includes smb`() {
        val result = TestPumpEnactResult().apply {
            bolusDelivered = 0.1
        }

        val json = result.json(baseBasal = 1.0)

        assertThat(json.getDouble("smb")).isEqualTo(0.1)
    }

    @Test
    fun `json with large bolus includes smb`() {
        val result = TestPumpEnactResult().apply {
            bolusDelivered = 25.0
        }

        val json = result.json(baseBasal = 1.0)

        assertThat(json.getDouble("smb")).isEqualTo(25.0)
    }

    @Test
    fun `json with very small absolute rate`() {
        val result = TestPumpEnactResult().apply {
            absolute = 0.05
            duration = 30
        }

        val json = result.json(baseBasal = 1.0)

        // Absolute temp puts value in "rate" field, not "absolute"
        assertThat(json.getDouble("rate")).isEqualTo(0.05)
    }

    @Test
    fun `json with large absolute rate`() {
        val result = TestPumpEnactResult().apply {
            absolute = 15.0
            duration = 30
        }

        val json = result.json(baseBasal = 1.0)

        // Absolute temp puts value in "rate" field, not "absolute"
        assertThat(json.getDouble("rate")).isEqualTo(15.0)
    }

    @Test
    fun `json with zero duration for absolute temp`() {
        val result = TestPumpEnactResult().apply {
            absolute = 2.0
            duration = 0
        }

        val json = result.json(baseBasal = 1.0)

        // Absolute temp puts value in "rate" field, not "absolute"
        assertThat(json.getDouble("rate")).isEqualTo(2.0)
        assertThat(json.getInt("duration")).isEqualTo(0)
    }

    @Test
    fun `json with percent handles fractional baseBasal`() {
        val result = TestPumpEnactResult().apply {
            isPercent = true
            percent = 120
            duration = 30
        }

        val json = result.json(baseBasal = 0.85)

        // 120% of 0.85 = 1.02
        assertThat(json.getDouble("rate")).isEqualTo(1.02)
    }

    @Test
    fun `json with percent 100 equals baseBasal`() {
        val result = TestPumpEnactResult().apply {
            isPercent = true
            percent = 100
            duration = 30
        }

        val json = result.json(baseBasal = 1.25)

        // 100% of 1.25 = 1.25
        assertThat(json.getDouble("rate")).isEqualTo(1.25)
    }

    @Test
    fun `json with percent 50 is half of baseBasal`() {
        val result = TestPumpEnactResult().apply {
            isPercent = true
            percent = 50
            duration = 30
        }

        val json = result.json(baseBasal = 2.0)

        // 50% of 2.0 = 1.0
        assertThat(json.getDouble("rate")).isEqualTo(1.0)
    }
}
