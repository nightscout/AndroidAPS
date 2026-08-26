package app.aaps.plugins.automation

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every component this module declares must name a class that exists.
 *
 * `:app` has the same check in `ManifestComponentsTest`, but it reads only its own manifest, so the
 * location service and the reminder receiver stopped being covered when they moved back here. That
 * check exists because a real bug got through once: a class was moved into `:app` and the manifest was
 * left pointing at the old name, so the component silently never ran.
 */
class AutomationManifestTest {

    private val componentTags = setOf("activity", "activity-alias", "service", "receiver", "provider")

    @Test
    fun everyDeclaredComponentClassExists() {
        val manifest = File("src/main/AndroidManifest.xml").absoluteFile
        assertThat(manifest.isFile).isTrue()

        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)
        val missing = mutableListOf<String>()
        var checked = 0

        for (tag in componentTags) {
            val nodes = document.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as? Element ?: continue
                val name = element.getAttribute("android:name").takeIf { it.isNotBlank() } ?: continue
                checked++
                runCatching { Class.forName(name, false, javaClass.classLoader) }
                    .onFailure { missing += "$tag -> $name" }
            }
        }

        assertThat(missing).isEmpty()
        // Guards the guard: if the manifest is ever emptied, this test must not silently pass.
        assertThat(checked).isAtLeast(2)
    }
}
