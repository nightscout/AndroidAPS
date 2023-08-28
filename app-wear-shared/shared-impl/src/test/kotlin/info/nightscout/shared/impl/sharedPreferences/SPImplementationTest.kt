package info.nightscout.shared.impl.sharedPreferences

import android.content.Context
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.mocks.SharedPreferencesMock
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

class SPImplementationTest : TestBase() {

    private val sharedPreferences: SharedPreferencesMock = SharedPreferencesMock()
    @Mock lateinit var context: Context

    private lateinit var sut: SPImplementation

    private val someResource = 1
    private val someResource2 = 2

    @BeforeEach
    fun setUp() {
        sut = SPImplementation(sharedPreferences, context)
        Mockito.`when`(context.getString(someResource)).thenReturn("some_resource")
        Mockito.`when`(context.getString(someResource2)).thenReturn("some_resource_2")
    }

    @Test
    fun edit() {
        sut.edit { putBoolean("test", true) }
        Assertions.assertTrue(sut.getBoolean("test", false))
        sut.edit { remove("test") }
        Assertions.assertFalse(sut.contains("test"))
        sut.edit { putBoolean(someResource, true) }
        Assertions.assertTrue(sut.getBoolean(someResource, false))
        sut.edit { remove(someResource) }
        Assertions.assertFalse(sut.contains(someResource))

        sut.edit(commit = true) { putDouble("test", 1.0) }
        Assertions.assertEquals(1.0, sut.getDouble("test", 2.0))
        sut.edit { putDouble(someResource, 1.0) }
        Assertions.assertEquals(1.0, sut.getDouble(someResource, 2.0))
        sut.edit { clear() }
        Assertions.assertFalse(sut.contains(someResource2))

        sut.edit { putInt("test", 1) }
        Assertions.assertEquals(1, sut.getInt("test", 2))
        sut.edit { putInt(someResource, 1) }
        Assertions.assertEquals(1, sut.getInt(someResource, 2))
        sut.edit { clear() }

        sut.edit { putLong("test", 1L) }
        Assertions.assertEquals(1L, sut.getLong("test", 2L))
        sut.edit { putLong(someResource, 1) }
        Assertions.assertEquals(1L, sut.getLong(someResource, 2L))
        sut.edit { clear() }

        sut.edit { putString("test", "string") }
        Assertions.assertEquals("string", sut.getString("test", "a"))
        sut.edit { putString(someResource, "string") }
        Assertions.assertEquals("string", sut.getString(someResource, "a"))
        sut.edit { clear() }
    }

    @Test
    fun clear() {
        sut.putBoolean("test", true)
        Assertions.assertTrue(sut.getAll().containsKey("test"))
        sut.clear()
        Assertions.assertFalse(sut.getAll().containsKey("test"))
    }

    @Test
    fun contains() {
        sut.putBoolean("test", true)
        Assertions.assertTrue(sut.contains("test"))
        sut.putBoolean(someResource, true)
        Assertions.assertTrue(sut.contains(someResource))
    }

    @Test
    fun remove() {
        sut.putBoolean("test", true)
        sut.remove("test")
        Assertions.assertFalse(sut.contains("test"))
        sut.putBoolean(someResource, true)
        sut.remove(someResource)
        Assertions.assertFalse(sut.contains(someResource))
    }

    @Test
    fun getString() {
        sut.putString("test", "string")
        Assertions.assertTrue(sut.getString("test", "") == "string")
        Assertions.assertTrue(sut.getString("test1", "") == "")
        sut.putString(someResource, "string")
        Assertions.assertTrue(sut.getString(someResource, "") == "string")
        Assertions.assertTrue(sut.getString(someResource2, "") == "")
    }

    @Test
    fun getStringOrNull() {
        sut.putString("test", "string")
        Assertions.assertTrue(sut.getStringOrNull("test", "") == "string")
        Assertions.assertNull(sut.getStringOrNull("test1", null))
        sut.putString(someResource, "string")
        Assertions.assertTrue(sut.getStringOrNull(someResource, null) == "string")
        Assertions.assertNull(sut.getStringOrNull(someResource2, null))
    }

    @Test
    fun getBoolean() {
        sut.putBoolean("test", true)
        Assertions.assertTrue(sut.getBoolean("test", false))
        sut.putBoolean(someResource, true)
        Assertions.assertTrue(sut.getBoolean(someResource, false))
        sut.putString("string_key", "a")
        Assertions.assertTrue(sut.getBoolean("string_key", true))
        sut.putString(someResource, "a")
        Assertions.assertTrue(sut.getBoolean(someResource, true))
    }

    @Test
    fun getDouble() {
        sut.putDouble("test", 1.0)
        Assertions.assertEquals(1.0, sut.getDouble("test", 2.0))
        Assertions.assertEquals(2.0, sut.getDouble("test1", 2.0))
        sut.putDouble(someResource, 1.0)
        Assertions.assertEquals(1.0, sut.getDouble(someResource, 2.0))
        Assertions.assertEquals(2.0, sut.getDouble(someResource2, 2.0))
        sut.putString("string_key", "a")
        Assertions.assertEquals(1.0, sut.getDouble("string_key", 1.0))
        sut.putString(someResource, "a")
        Assertions.assertEquals(1.0, sut.getDouble(someResource, 1.0))
    }

    @Test
    fun getInt() {
        sut.putInt("test", 1)
        Assertions.assertEquals(1, sut.getInt("test", 2))
        Assertions.assertEquals(2, sut.getInt("test1", 2))
        sut.putInt(someResource, 1)
        Assertions.assertEquals(1, sut.getInt(someResource, 2))
        Assertions.assertEquals(2, sut.getInt(someResource2, 2))
        sut.putString("string_key", "a")
        Assertions.assertEquals(1, sut.getInt("string_key", 1))
        sut.putString(someResource, "a")
        Assertions.assertEquals(1, sut.getInt(someResource, 1))
    }

    @Test
    fun getLong() {
        sut.putLong("test", 1L)
        Assertions.assertEquals(1L, sut.getLong("test", 2L))
        Assertions.assertEquals(2L, sut.getLong("test1", 2L))
        sut.putLong(someResource, 1L)
        Assertions.assertEquals(1L, sut.getLong(someResource, 2L))
        Assertions.assertEquals(2L, sut.getLong(someResource2, 2L))
        sut.putString("string_key", "a")
        Assertions.assertEquals(1L, sut.getLong("string_key", 1L))
        sut.putString(someResource, "a")
        Assertions.assertEquals(1L, sut.getLong(someResource, 1L))
    }

    @Test
    fun incLong() {
        sut.incLong(someResource)
        Assertions.assertEquals(1L, sut.getLong(someResource, 3L))
        sut.incLong(someResource)
        Assertions.assertEquals(2L, sut.getLong(someResource, 3L))
    }

    @Test
    fun incInt() {
        sut.incInt(someResource)
        Assertions.assertEquals(1, sut.getInt(someResource, 3))
        sut.incInt(someResource)
        Assertions.assertEquals(2, sut.getInt(someResource, 3))
    }
}