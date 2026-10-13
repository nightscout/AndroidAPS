package app.aaps.plugins.sync.nsShared

import app.aaps.shared.tests.TestBase
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NSSgvTest : TestBase() {

    @Test
    fun `parses all fields from a complete and valid JSON object`() {
        // Arrange
        val json = JSONObject().apply {
            put("_id", "test_id_123")
            put("mgdl", 105)
            put("mills", 1455136282375L)
            put("device", "xDrip-BluetoothWixel")
            put("direction", "Flat")
            put("filtered", 98272)
            put("unfiltered", 98272) // This field is not parsed by the class, just for completeness
            put("noise", 1)
            put("rssi", 100) // This field is not parsed by the class, just for completeness
        }

        // Act
        val nsSgv = NSSgv(json)

        // Assert
        assertEquals("test_id_123", nsSgv.id)
        assertEquals(105, nsSgv.mgdl)
        assertEquals(1455136282375L, nsSgv.mills)
        assertEquals("xDrip-BluetoothWixel", nsSgv.device)
        assertEquals("Flat", nsSgv.direction)
        assertEquals(98272, nsSgv.filtered)
        assertEquals(1, nsSgv.noise)
    }

    @Test
    fun `returns null for all properties when JSON object is empty`() {
        // Arrange
        val emptyJson = JSONObject()

        // Act
        val nsSgv = NSSgv(emptyJson)

        // Assert
        assertNull(nsSgv.id, "ID should be null for empty JSON")
        assertNull(nsSgv.mgdl, "mgdl should be null for empty JSON")
        assertNull(nsSgv.mills, "mills should be null for empty JSON")
        assertNull(nsSgv.device, "device should be null for empty JSON")
        assertNull(nsSgv.direction, "direction should be null for empty JSON")
        assertNull(nsSgv.filtered, "filtered should be null for empty JSON")
        assertNull(nsSgv.noise, "noise should be null for empty JSON")
    }

    @Test
    fun `returns null for properties that are explicitly set to JSON null`() {
        // Arrange
        val jsonWithNulls = JSONObject().apply {
            put("_id", "test_id_null")
            put("mgdl", JSONObject.NULL)
            put("mills", JSONObject.NULL)
            put("device", JSONObject.NULL)
            put("direction", JSONObject.NULL)
            put("filtered", JSONObject.NULL)
            put("noise", JSONObject.NULL)
        }

        // Act
        val nsSgv = NSSgv(jsonWithNulls)

        // Assert
        assertEquals("test_id_null", nsSgv.id) // ID is present
        assertNull(nsSgv.mgdl, "mgdl should be null when JSON value is null")
        assertNull(nsSgv.mills, "mills should be null when JSON value is null")
        assertNull(nsSgv.device, "device should be null when JSON value is null")
        assertNull(nsSgv.direction, "direction should be null when JSON value is null")
        assertNull(nsSgv.filtered, "filtered should be null when JSON value is null")
        assertNull(nsSgv.noise, "noise should be null when JSON value is null")
    }

    @Test
    fun `handles JSON with only a subset of fields`() {
        // Arrange
        val partialJson = JSONObject().apply {
            put("mgdl", 120)
            put("mills", 1672531200000L)
        }

        // Act
        val nsSgv = NSSgv(partialJson)

        // Assert
        assertEquals(120, nsSgv.mgdl)
        assertEquals(1672531200000L, nsSgv.mills)
        assertNull(nsSgv.id, "ID should be null when missing")
        assertNull(nsSgv.device, "device should be null when missing")
        assertNull(nsSgv.direction, "direction should be null when missing")
        assertNull(nsSgv.filtered, "filtered should be null when missing")
        assertNull(nsSgv.noise, "noise should be null when missing")
    }

    @Test
    fun `returns null for properties with incorrect data types`() {
        // Arrange
        val malformedJson = JSONObject().apply {
            put("mgdl", "not-a-number")
            put("mills", "not-a-long")
            put("filtered", "not-an-int")
            put("noise", true) // boolean instead of int
        }

        // Act
        val nsSgv = NSSgv(malformedJson)

        // Assert
        assertNull(nsSgv.mgdl, "mgdl should be null for wrong data type")
        assertNull(nsSgv.mills, "mills should be null for wrong data type")
        assertNull(nsSgv.filtered, "filtered should be null for wrong data type")
        assertNull(nsSgv.noise, "noise should be null for wrong data type")
    }
}
