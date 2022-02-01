package info.nightscout.androidaps.utils.serialisation

import info.nightscout.androidaps.database.entities.ValueWithUnit
import org.junit.Assert
import org.junit.Test

internal class ValueWithUnitSerialiserTest {

    @Test
    fun testSerialisationDeserization() {

        val list = listOf<ValueWithUnit>(
            ValueWithUnit.SimpleString("hello"),
            ValueWithUnit.SimpleInt(5),
            ValueWithUnit.UNKNOWN
        )

        val serialized = ValueWithUnitSerialiser.toSealedClassJson(list)
        val deserialized = ValueWithUnitSerialiser.fromJson(serialized)

        Assert.assertEquals(3, list.size)
        Assert.assertEquals(list, deserialized)
    }

    @Test
    fun testEmptyList() {

        val list = listOf<ValueWithUnit>()

        val serialized = ValueWithUnitSerialiser.toSealedClassJson(list)
        val deserialized = ValueWithUnitSerialiser.fromJson(serialized)

        Assert.assertEquals(0, list.size)
        Assert.assertEquals(list, deserialized)
    }
}