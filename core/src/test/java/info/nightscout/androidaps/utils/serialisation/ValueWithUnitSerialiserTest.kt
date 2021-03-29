package info.nightscout.androidaps.utils.serialisation

import info.nightscout.androidaps.database.entities.XXXValueWithUnit
import org.junit.Assert
import org.junit.Test

internal class ValueWithUnitSerialiserTest {

    @Test
    fun testSerialisationDeserization() {

        val list = listOf<XXXValueWithUnit>(
            XXXValueWithUnit.SimpleString("hello"),
            XXXValueWithUnit.SimpleInt(5),
            XXXValueWithUnit.UNKNOWN
        )

        val serialized = ValueWithUnitSerialiser.toSealedClassJson(list)
        val deserialized = ValueWithUnitSerialiser.fromJson(serialized)

        Assert.assertEquals(3, list.size)
        Assert.assertEquals(list, deserialized)
    }

    @Test
    fun testEmptyList() {

        val list = listOf<XXXValueWithUnit>()

        val serialized = ValueWithUnitSerialiser.toSealedClassJson(list)
        val deserialized = ValueWithUnitSerialiser.fromJson(serialized)

        Assert.assertEquals(0, list.size)
        Assert.assertEquals(list, deserialized)
    }
}