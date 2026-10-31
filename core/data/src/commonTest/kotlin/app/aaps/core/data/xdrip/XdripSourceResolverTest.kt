package app.aaps.core.data.xdrip

import app.aaps.core.data.model.SourceSensor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * In `commonTest`, so this runs through Kotlin/Native as well as the JVM. `kotlin.test` rather than
 * Truth and JUnit 5, because neither of those exists off the JVM.
 */
class XdripSourceResolverTest {

    @Test
    fun `a device written by another app falls back to plain matching`() {
        assertEquals(SourceSensor.DEXCOM_G6, XdripSourceResolver.fromNightscoutDevice("G6 Native"))
        assertEquals(SourceSensor.LIBRE_2, XdripSourceResolver.fromNightscoutDevice("Libre2"))
        assertEquals(SourceSensor.DEXCOM_G7, XdripSourceResolver.fromNightscoutDevice("Dexcom G7"))
    }

    @Test
    fun `device with the appended sensor name resolves`() {
        assertEquals(SourceSensor.DEXCOM_G6, XdripSourceResolver.fromNightscoutDevice("xDrip-DexcomG5 G6 Native"))
        assertEquals(SourceSensor.DEXCOM_G6, XdripSourceResolver.fromNightscoutDevice("xDrip-DexcomG5 G6 Native::Backfill"))
        assertEquals(SourceSensor.LIBRE_2, XdripSourceResolver.fromNightscoutDevice("xDrip-LibreReceiver Libre2 Native"))
    }

    @Test
    fun `the appended sensor name is preferred over the collection method`() {
        // The method only says Dexcom, the appended name says which one.
        assertEquals(SourceSensor.DEXCOM_G7, XdripSourceResolver.fromNightscoutDevice("xDrip-DexcomG5 G7 Native"))
    }

    @Test
    fun `device with only the collection method resolves`() {
        assertEquals(SourceSensor.DEXCOM_UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xDrip-DexcomG5"))
        assertEquals(SourceSensor.DEXCOM_UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xDrip-SHFollower"))
        assertEquals(SourceSensor.MEDTRUM_UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xDrip-Medtrum"))
        assertEquals(SourceSensor.MM_UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xDrip-CLFollower"))
        assertEquals(SourceSensor.GLUPRO, XdripSourceResolver.fromNightscoutDevice("xDrip-GluPro"))
        assertEquals(SourceSensor.AIDEX, XdripSourceResolver.fromNightscoutDevice("xDrip-AidexReceiver"))
        assertEquals(SourceSensor.LIBRE_2, XdripSourceResolver.fromNightscoutDevice("xDrip-LibreReceiver"))
        assertEquals(SourceSensor.LIBRE_1, XdripSourceResolver.fromNightscoutDevice("xDrip-LimiTTer"))
        assertEquals(SourceSensor.LIBRE_1, XdripSourceResolver.fromNightscoutDevice("xDrip-LibreWifi"))
    }

    @Test
    fun `the method is used when the appended sensor name is not known`() {
        assertEquals(SourceSensor.LIBRE_1, XdripSourceResolver.fromNightscoutDevice("xDrip-LimiTTer Something Else"))
    }

    @Test
    fun `case is ignored in the method`() {
        assertEquals(SourceSensor.MEDTRUM_UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xdrip-medtrum"))
    }

    @Test
    fun `methods that do not identify a sensor stay unknown`() {
        assertEquals(SourceSensor.UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xDrip-NSFollower"))
        assertEquals(SourceSensor.UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xDrip-WebFollower"))
        assertEquals(SourceSensor.UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xDrip-Follower"))
        assertEquals(SourceSensor.UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xDrip-NSEmulator"))
        assertEquals(SourceSensor.UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xDrip-BluetoothWixel"))
        assertEquals(SourceSensor.UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xDrip-LibreAlarm"))
        assertEquals(SourceSensor.UNKNOWN, XdripSourceResolver.fromNightscoutDevice("xDrip-UiBased"))
    }

    @Test
    fun `the prefix is required before a method is looked up`() {
        assertEquals(SourceSensor.UNKNOWN, XdripSourceResolver.fromNightscoutDevice("not xDrip-Medtrum"))
        assertEquals(SourceSensor.UNKNOWN, XdripSourceResolver.fromNightscoutDevice("Medtrum something"))
    }

    @Test
    fun `missing and empty values are unknown`() {
        assertEquals(SourceSensor.UNKNOWN, XdripSourceResolver.fromNightscoutDevice(null))
        assertEquals(SourceSensor.UNKNOWN, XdripSourceResolver.fromNightscoutDevice(""))
    }
}
