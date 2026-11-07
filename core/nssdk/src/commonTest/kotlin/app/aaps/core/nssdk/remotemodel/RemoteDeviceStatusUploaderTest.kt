package app.aaps.core.nssdk.remotemodel

import app.aaps.core.nssdk.mapper.toNSDeviceStatus
import app.aaps.core.nssdk.nsSdkJson
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `uploader` field of a device status, which not every uploader writes as an object.
 *
 * On 4.0.0-dev-e a Nightscout site held device statuses with `"uploader":"GG-Host"`. Each one crashed
 * the app when it came in on the websocket, and on the REST load it failed the whole list it was in.
 * In commonTest, so the iOS client reads these documents the same way.
 */
class RemoteDeviceStatusUploaderTest {

    @Test
    fun aStringUploaderReadsAsNoBatteryAndTheRestOfTheDocumentIsKept() {
        val json = """{"uploaderBattery":100,"uploader":"GG-Host","identifier":"44d787","srvModified":1000,"pump":{"reservoir":12.5}}"""

        val status = json.toNSDeviceStatus()

        assertNull(status.uploader?.battery)
        assertEquals(100, status.uploaderBattery)
        assertEquals("44d787", status.identifier)
        assertEquals(12.5, status.pump?.reservoir)
    }

    @Test
    fun anUploaderObjectStillGivesItsBattery() {
        val status = """{"uploader":{"battery":55}}""".toNSDeviceStatus()

        assertEquals(55, status.uploader?.battery)
    }

    @Test
    fun otherShapesReadAsNoBattery() {
        for (value in listOf("5", "true", "[1,2]"))
            assertNull("""{"uploader":$value}""".toNSDeviceStatus().uploader?.battery, "uploader = $value")
    }

    @Test
    fun aNullUploaderStaysNull() {
        val remote = nsSdkJson.decodeFromString(RemoteDeviceStatus.serializer(), """{"uploader":null}""")

        assertNull(remote.uploader)
    }

    /** The REST load reads a list. One odd document must not cost the others. */
    @Test
    fun aStringUploaderDoesNotFailTheListItIsIn() {
        val json = """[{"identifier":"a","uploader":"GG-Host"},{"identifier":"b","uploader":{"battery":80}}]"""

        val list = nsSdkJson.decodeFromString(ListSerializer(RemoteDeviceStatus.serializer()), json)

        assertEquals(listOf("a", "b"), list.map { it.identifier })
        assertEquals(80, list[1].uploader?.battery)
    }

    /** Only reading is lenient. What AAPS uploads keeps the object shape. */
    @Test
    fun writingKeepsTheObject() {
        val json = nsSdkJson.encodeToString(RemoteDeviceStatus.serializer(), RemoteDeviceStatus(uploader = RemoteDeviceStatus.Uploader(battery = 77)))

        assertTrue(json.contains(""""uploader":{"battery":77}"""), json)
    }
}
