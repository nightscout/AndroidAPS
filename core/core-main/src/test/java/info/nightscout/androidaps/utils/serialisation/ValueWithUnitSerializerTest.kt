package info.nightscout.androidaps.utils.serialisation

import info.nightscout.database.entities.ValueWithUnit
import org.junit.Assert
import org.junit.Test

internal class ValueWithUnitSerializerTest {

    @Test
    fun testSerialisationDeserization() {

        val list = listOf<ValueWithUnit>(
            ValueWithUnit.SimpleString("hello"),
            ValueWithUnit.SimpleInt(5),
            ValueWithUnit.UNKNOWN
        )

        val serialized = ValueWithUnitSerializer.toSealedClassJson(list)
        val deserialized = ValueWithUnitSerializer.fromJson(serialized)

        Assert.assertEquals(3, list.size)
        Assert.assertEquals(list, deserialized)
    }

    @Test
    fun testEmptyList() {

        val list = listOf<ValueWithUnit>()

        val serialized = ValueWithUnitSerializer.toSealedClassJson(list)
        val deserialized = ValueWithUnitSerializer.fromJson(serialized)

        Assert.assertEquals(0, list.size)
        Assert.assertEquals(list, deserialized)
    }
}