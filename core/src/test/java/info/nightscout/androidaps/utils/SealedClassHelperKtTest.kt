package info.nightscout.androidaps.utils

import info.nightscout.androidaps.database.entities.XXXValueWithUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SealedClassHelperKtTest {



    @Test
    fun testSerialisationDeserization() {

        val list = listOf<XXXValueWithUnit>(
            XXXValueWithUnit.SimpleString("hello"),
            XXXValueWithUnit.SimpleInt(5),
            XXXValueWithUnit.UNKNOWN
        )

        val serialized = toSealedClassJson(list)
        val deserialized = fromJson(serialized)

        assertEquals(3, list.size)
        assertEquals(list, deserialized)
    }

    @Test
    fun testEmptyList() {

        val list = listOf<XXXValueWithUnit>()

        val serialized = toSealedClassJson(list)
        val deserialized = fromJson(serialized)

        assertEquals(0, list.size)
        assertEquals(list, deserialized)
    }
}