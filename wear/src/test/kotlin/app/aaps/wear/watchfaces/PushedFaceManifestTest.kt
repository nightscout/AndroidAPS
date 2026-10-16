package app.aaps.wear.watchfaces

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The manifest names the pushed watch faces in two places, and both have to agree with
 * [PushedFace].
 *
 * This exists because it already went wrong. Renaming the face id from `aapsv4` to `wfs` and `cwf`
 * left ten strings in the manifest naming a package that no longer exists, and nothing said a word:
 * the ids live in XML as text, so no compiler compares them with the enum. On a watch the missing
 * `<queries>` entry hid the installed face from `getPackageInfo`, which reports it as absent - so
 * the menu believed the face missing and offered to install it again on every visit.
 *
 * The enum is the single source of truth here. This ties the manifest to it; it cannot tell whether
 * the ids themselves are the ones the watch expects.
 */
class PushedFaceManifestTest {

    /** What the manifest must say, in its own words - `${'$'}{applicationId}` is substituted by the merger. */
    private val expected = PushedFace.entries.map { "\${applicationId}.watchfacepush.${it.id}" }.toSet()

    private val document by lazy {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(findManifest())
    }

    @Test
    fun `queries names every pushed face, so an installed one stays visible`() {
        val queries = document.getElementsByTagName("queries")
        val declared = mutableSetOf<String>()
        for (i in 0 until queries.length) {
            val packages = (queries.item(i) as Element).getElementsByTagName("package")
            for (j in 0 until packages.length) {
                val name = (packages.item(j) as Element).getAttribute("android:name")
                if (".watchfacepush." in name) declared.add(name)
            }
        }

        assertThat(declared).isEqualTo(expected)
    }

    @Test
    fun `every SAFE_WATCH_FACES entry lists exactly the pushed faces`() {
        val metaData = document.getElementsByTagName("meta-data")
        val values = mutableListOf<String>()
        for (i in 0 until metaData.length) {
            val element = metaData.item(i) as Element
            if (element.getAttribute("android:name") == SAFE_WATCH_FACES)
                values.add(element.getAttribute("android:value"))
        }

        // A source that names no face at all would pass a set comparison over an empty list
        assertThat(values).isNotEmpty()
        values.forEach { assertThat(it.split(",").toSet()).isEqualTo(expected) }
    }

    /** Test working directories differ between Gradle and IDE runs, so walk up to find the module. */
    private fun findManifest(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val direct = File(dir, "src/main/AndroidManifest.xml")
            if (direct.isFile) return direct
            val fromRoot = File(dir, "wear/src/main/AndroidManifest.xml")
            if (fromRoot.isFile) return fromRoot
            dir = dir.parentFile
        }
        error("Cannot locate the wear AndroidManifest.xml from ${File("").absolutePath}")
    }

    private companion object {

        const val SAFE_WATCH_FACES = "android.support.wearable.complications.SAFE_WATCH_FACES"
    }
}
