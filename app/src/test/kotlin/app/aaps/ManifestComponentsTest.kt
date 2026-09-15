package app.aaps

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every `<activity>`, `<service>`, `<receiver>` and `<provider>` in the app manifest must name a class
 * that exists.
 *
 * This exists because it already went wrong. Moving `CarbSuggestionReceiver` out of :plugins:aps and
 * into :app left the manifest pointing at `app.aaps.plugins.aps.loop.CarbSuggestionReceiver`, a class
 * that no longer existed, while the real receiver was declared nowhere. Android drops an explicit
 * broadcast to an undeclared receiver without an error, so the "ignore carb suggestion for 5m/15m/30m"
 * notification buttons simply stopped working, and nothing - not the build, not a test, not a lint
 * run - said a word. Moving components between modules is a normal part of the multiplatform
 * migration, so the same mistake is easy to repeat.
 *
 * Only component elements are checked. `android:name` also names permissions, intent-filter actions
 * and metadata keys, none of which are classes.
 */
class ManifestComponentsTest {

    private val componentTags = setOf("activity", "activity-alias", "service", "receiver", "provider")

    @Test
    fun everyDeclaredComponentClassExists() {
        val manifest = findManifest()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)

        val missing = mutableListOf<String>()
        var checked = 0

        for (tag in componentTags) {
            val nodes = document.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as? Element ?: continue
                val name = element.getAttribute("android:name").takeIf { it.isNotBlank() } ?: continue
                // A leading dot is relative to the manifest package; the merger resolves it. Nothing in
                // this manifest uses that form, and resolving it here would just duplicate the merger.
                if (name.startsWith(".")) continue
                checked++
                val exists = runCatching { Class.forName(name, false, javaClass.classLoader) }.isSuccess
                if (!exists) missing.add("<$tag> $name")
            }
        }

        assertThat(checked).isGreaterThan(0)
        assertThat(missing).isEmpty()
    }

    /** Test working directories differ between Gradle and IDE runs, so walk up to find the module. */
    private fun findManifest(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val direct = File(dir, "src/main/AndroidManifest.xml")
            if (direct.isFile) return direct
            val fromRoot = File(dir, "app/src/main/AndroidManifest.xml")
            if (fromRoot.isFile) return fromRoot
            dir = dir.parentFile
        }
        error("Cannot locate the app AndroidManifest.xml from ${File("").absolutePath}")
    }
}
