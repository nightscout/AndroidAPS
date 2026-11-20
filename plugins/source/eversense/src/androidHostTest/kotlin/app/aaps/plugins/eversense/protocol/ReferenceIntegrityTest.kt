package app.aaps.plugins.eversense.protocol

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The oracle must stay independent from later changes to production code. */
class ReferenceIntegrityTest {
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun frozenSourcesHaveTheRecordedHashes() {
        val archive = checkNotNull(javaClass.getResourceAsStream("/protocol-reference.zip")).use { it.readBytes() }
        assertEquals("148f63d0a9375f01b9e0c0b67c75148a1bfe46d931842da462298d3023444f93", sha256(archive))
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
            }
        }
        val manifest = Json.parseToJsonElement(checkNotNull(entries["baseline.json"]).decodeToString()).jsonObject
        assertEquals("bb0b0373cd6ccf6b935a63783cb5fcf29468e317", manifest.getValue("baseline_commit").jsonPrimitive.content)
        val commands = checkNotNull(javaClass.getResourceAsStream("/protocol-command-classes.txt"))
            .bufferedReader().use { it.readLines() }
        assertEquals(109, commands.size)
        assertEquals(109, commands.toSet().size)
        assertEquals(manifest.getValue("commands").jsonArray.map { it.jsonPrimitive.content }.sorted(), commands.sorted())
        val files = manifest.getValue("files").jsonArray
        assertEquals(156, files.size)
        for (file in files) {
            val item = file.jsonObject
            val path = item.getValue("path").jsonPrimitive.content
            assertEquals(item.getValue("sha256").jsonPrimitive.content, sha256(checkNotNull(entries[path])), path)
        }
        assertEquals(files.size, entries.keys.count { it.endsWith(".kt") })
    }
}
