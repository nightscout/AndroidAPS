package app.aaps.plugins.sync.nsShared.extensions

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GVExtensionTest : TestBase() {

    private fun createBaseGV(
        id: Long = 1L,
        timestamp: Long = 1672531200000L, // Jan 1, 2023 00:00:00 GMT
        value: Double = 120.0,
        isValid: Boolean = true,
        utcOffset: Long = 0,
        raw: Double = 120000.0,
        trendArrow: TrendArrow = TrendArrow.FLAT,
        noise: Double = 1.0,
        sourceSensor: SourceSensor = SourceSensor.DEXCOM_G6_NATIVE,
        nightscoutId: String? = null
    ) = GV(
        id = id,
        timestamp = timestamp,
        value = value,
        isValid = isValid,
        utcOffset = utcOffset,
        raw = raw,
        trendArrow = trendArrow,
        noise = noise,
        sourceSensor = sourceSensor,
        ids = IDs(nightscoutId = nightscoutId)
    )

    // region Tests for contentEqualsTo

    @Test
    fun `contentEqualsTo returns true for identical GV objects`() {
        // Arrange
        val gv1 = createBaseGV()
        val gv2 = createBaseGV()

        // Act & Assert
        assertTrue(gv1.contentEqualsTo(gv2))
    }

    @Test
    fun `contentEqualsTo returns false if timestamp differs`() {
        // Arrange
        val gv1 = createBaseGV()
        val gv2 = createBaseGV(timestamp = gv1.timestamp + 1)

        // Act & Assert
        assertFalse(gv1.contentEqualsTo(gv2))
    }

    @Test
    fun `contentEqualsTo returns false if value differs`() {
        // Arrange
        val gv1 = createBaseGV()
        val gv2 = createBaseGV(value = gv1.value + 1.0)

        // Act & Assert
        assertFalse(gv1.contentEqualsTo(gv2))
    }

    @Test
    fun `contentEqualsTo returns false if isValid differs`() {
        // Arrange
        val gv1 = createBaseGV()
        val gv2 = createBaseGV(isValid = !gv1.isValid)

        // Act & Assert
        assertFalse(gv1.contentEqualsTo(gv2))
    }

    @Test
    fun `contentEqualsTo returns false if trendArrow differs`() {
        // Arrange
        val gv1 = createBaseGV(trendArrow = TrendArrow.FLAT)
        val gv2 = createBaseGV(trendArrow = TrendArrow.FORTY_FIVE_UP)

        // Act & Assert
        assertFalse(gv1.contentEqualsTo(gv2))
    }

    @Test
    fun `contentEqualsTo returns false if sourceSensor differs`() {
        // Arrange
        val gv1 = createBaseGV(sourceSensor = SourceSensor.DEXCOM_G6_NATIVE)
        val gv2 = createBaseGV(sourceSensor = SourceSensor.DEXCOM_G7_NATIVE)

        // Act & Assert
        assertFalse(gv1.contentEqualsTo(gv2))
    }

    @Test
    fun `contentEqualsTo ignores differences in id and nightscoutId`() {
        // Arrange
        val gv1 = createBaseGV(id = 1L, nightscoutId = null)
        val gv2 = createBaseGV(id = 2L, nightscoutId = "some-ns-id")

        // Act & Assert
        // All other content fields are the same, so this should be true
        assertTrue(gv1.contentEqualsTo(gv2))
    }

    // endregion

    // region Tests for onlyNsIdAdded

    @Test
    fun `onlyNsIdAdded returns true for the exact intended scenario`() {
        // Arrange
        val previous = createBaseGV(id = 10L, nightscoutId = null)
        val current = createBaseGV(id = 11L, nightscoutId = "new-ns-id-123") // Different local ID, new NS ID

        // Act & Assert
        assertTrue(current.onlyNsIdAdded(previous))
    }

    @Test
    fun `onlyNsIdAdded returns false if content is not equal`() {
        // Arrange
        val previous = createBaseGV(id = 10L, nightscoutId = null)
        val current = createBaseGV(id = 11L, nightscoutId = "new-ns-id-123", value = 125.0) // Value is different

        // Act & Assert
        assertFalse(current.onlyNsIdAdded(previous))
    }

    @Test
    fun `onlyNsIdAdded returns false if the local id is the same`() {
        // Arrange
        val previous = createBaseGV(id = 10L, nightscoutId = null)
        val current = createBaseGV(id = 10L, nightscoutId = "new-ns-id-123") // Same local ID

        // Act & Assert
        assertFalse(current.onlyNsIdAdded(previous))
    }

    @Test
    fun `onlyNsIdAdded returns false if previous GV already had a nightscoutId`() {
        // Arrange
        val previous = createBaseGV(id = 10L, nightscoutId = "existing-ns-id")
        val current = createBaseGV(id = 11L, nightscoutId = "new-ns-id-123")

        // Act & Assert
        assertFalse(current.onlyNsIdAdded(previous))
    }

    @Test
    fun `onlyNsIdAdded returns false if current GV does not have a nightscoutId`() {
        // Arrange
        val previous = createBaseGV(id = 10L, nightscoutId = null)
        val current = createBaseGV(id = 11L, nightscoutId = null) // Still no NS ID

        // Act & Assert
        assertFalse(current.onlyNsIdAdded(previous))
    }

    // endregion
}
