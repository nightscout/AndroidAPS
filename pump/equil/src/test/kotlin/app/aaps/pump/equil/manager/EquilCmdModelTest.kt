package app.aaps.pump.equil.manager

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EquilCmdModelTest : TestBase() {

    private lateinit var model: EquilCmdModel

    @BeforeEach
    fun setUp() {
        model = EquilCmdModel()
    }

    @Test
    fun `default values should be null`() {
        assertNull(model.code)
        assertNull(model.iv)
        assertNull(model.tag)
        assertNull(model.ciphertext)
    }

    @Test
    fun `code should be settable`() {
        model.code = "0404"
        assertEquals("0404", model.code)
    }

    @Test
    fun `iv should be settable`() {
        model.iv = "0123456789ABCDEF"
        assertEquals("0123456789ABCDEF", model.iv)
    }

    @Test
    fun `tag should be settable`() {
        model.tag = "FEDCBA9876543210"
        assertEquals("FEDCBA9876543210", model.tag)
    }

    @Test
    fun `ciphertext should be settable`() {
        model.ciphertext = "AABBCCDDEEFF"
        assertEquals("AABBCCDDEEFF", model.ciphertext)
    }

    @Test
    fun `all properties should be independently settable`() {
        model.code = "0404"
        model.iv = "ABC123"
        model.tag = "DEF456"
        model.ciphertext = "789GHI"

        assertEquals("0404", model.code)
        assertEquals("ABC123", model.iv)
        assertEquals("DEF456", model.tag)
        assertEquals("789GHI", model.ciphertext)
    }

    @Test
    fun `properties should be mutable`() {
        model.code = "0101"
        model.code = "0202"
        assertEquals("0202", model.code)

        model.iv = "first"
        model.iv = "second"
        assertEquals("second", model.iv)
    }

    @Test
    fun `properties can be set to null`() {
        model.code = "test"
        model.code = null
        assertNull(model.code)

        model.iv = "test"
        model.iv = null
        assertNull(model.iv)

        model.tag = "test"
        model.tag = null
        assertNull(model.tag)

        model.ciphertext = "test"
        model.ciphertext = null
        assertNull(model.ciphertext)
    }

    @Test
    fun `toString should include all properties`() {
        model.code = "0404"
        model.iv = "ABC"
        model.tag = "DEF"
        model.ciphertext = "GHI"

        val str = model.toString()
        assert(str.contains("0404"))
        assert(str.contains("ABC"))
        assert(str.contains("DEF"))
        assert(str.contains("GHI"))
    }

    @Test
    fun `toString should handle null values`() {
        val str = model.toString()
        assert(str.contains("null") || str.contains("EquilCmdModel"))
    }

    @Test
    fun `toString should have expected format`() {
        model.code = "CODE"
        model.iv = "IV"
        model.tag = "TAG"
        model.ciphertext = "CIPHER"

        val str = model.toString()
        val expected = "EquilCmdModel{code='CODE', iv='IV', tag='TAG', ciphertext='CIPHER'}"
        assertEquals(expected, str)
    }

    @Test
    fun `multiple instances should be independent`() {
        val model1 = EquilCmdModel()
        val model2 = EquilCmdModel()

        model1.code = "CODE1"
        model1.iv = "IV1"

        model2.code = "CODE2"
        model2.iv = "IV2"

        assertEquals("CODE1", model1.code)
        assertEquals("IV1", model1.iv)
        assertEquals("CODE2", model2.code)
        assertEquals("IV2", model2.iv)
    }

    @Test
    fun `properties should handle empty strings`() {
        model.code = ""
        model.iv = ""
        model.tag = ""
        model.ciphertext = ""

        assertEquals("", model.code)
        assertEquals("", model.iv)
        assertEquals("", model.tag)
        assertEquals("", model.ciphertext)
    }

    @Test
    fun `properties should handle long strings`() {
        val longString = "A".repeat(1000)
        model.ciphertext = longString
        assertEquals(longString, model.ciphertext)
        assertEquals(1000, model.ciphertext?.length)
    }

    @Test
    fun `properties should handle hex strings`() {
        model.code = "0F0F"
        model.iv = "0123456789ABCDEF"
        model.tag = "FEDCBA9876543210"
        model.ciphertext = "DEADBEEF"

        assertEquals("0F0F", model.code)
        assertEquals("0123456789ABCDEF", model.iv)
        assertEquals("FEDCBA9876543210", model.tag)
        assertEquals("DEADBEEF", model.ciphertext)
    }

    @Test
    fun `properties should handle lowercase hex strings`() {
        model.code = "0f0f"
        model.iv = "abcdef123456"
        model.tag = "fedcba987654"
        model.ciphertext = "deadbeef"

        assertEquals("0f0f", model.code)
        assertEquals("abcdef123456", model.iv)
        assertEquals("fedcba987654", model.tag)
        assertEquals("deadbeef", model.ciphertext)
    }

    @Test
    fun `toString with partial data should work`() {
        model.code = "0404"
        model.iv = "ABC123"
        // tag and ciphertext remain null

        val str = model.toString()
        assert(str.contains("0404"))
        assert(str.contains("ABC123"))
    }
}
