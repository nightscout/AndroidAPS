package app.aaps.plugins.aps.autotune

import app.aaps.core.data.time.SystemTimeZone
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Assertions.fail
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant

/**
 * Golden files for the Autotune tests.
 *
 * They pin what Autotune writes and computes today, so moving the code to common Kotlin cannot
 * change a number, a time or the text of a file without a test failing. The expected text was made
 * by the code before the move and checked by hand.
 *
 * A missing golden file is written from the run and the test fails, so a new golden file is never
 * accepted without a look at it.
 */
internal object AutotuneGolden {

    private const val DIR = "src/androidHostTest/res/autotune/golden"

    fun assertMatches(name: String, actual: String) {
        val file = File(DIR, name)
        val text = actual.replace("\r\n", "\n")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText(text)
            fail<Unit>("Golden file $name did not exist. It is written now from this run. Check it by hand and run the test again.")
        }
        assertWithMessage("golden file $name").that(text).isEqualTo(file.readText().replace("\r\n", "\n"))
    }

    /** The time as UTC text, so a report does not depend on the zone the test runs in. */
    fun utc(timestamp: Long): String = Instant.fromEpochMilliseconds(timestamp).toString()

    /**
     * JSON text in a form that can be compared: the keys of each object sorted, everything else as
     * it was written.
     *
     * The order of the keys means nothing to a JSON reader, and it is the one thing the tests cannot
     * see as the phone writes it: the `org.json` used on the JVM here keeps its keys in a hash map,
     * while Android's own keeps the order they were put in. So only the order is taken out. Each
     * value keeps its exact text - `8` stays `8` and does not become `8.0`, an escaped `\/` stays
     * escaped - and the text is first checked to be laid out as `org.json` lays it out: the spaces,
     * the line breaks and the indent.
     */
    fun json(text: String): String {
        val indent = if (text.contains('\n')) text.lines()[1].takeWhile { it == ' ' }.length else 0
        val parser = JsonText(text)
        val value = parser.value()
        parser.end()
        assertWithMessage("the layout of\n$text").that(render(value, indent, sorted = false)).isEqualTo(text)
        return render(value, indent, sorted = true)
    }

    /** A JSON value. A string, number, `true`, `false` or `null` keeps its exact text. */
    private sealed class Node {
        class Literal(val text: String) : Node()
        class Members(val members: List<Pair<String, Node>>) : Node()
        class Items(val items: List<Node>) : Node()
    }

    private class JsonText(private val text: String) {

        private var at = 0

        fun end() {
            space()
            check(at == text.length) { "text after the JSON value at $at" }
        }

        private fun space() {
            while (at < text.length && text[at].isWhitespace()) at++
        }

        fun value(): Node {
            space()
            return when (text[at]) {
                '{'  -> members()
                '['  -> items()
                '"'  -> Node.Literal(string())
                else -> {
                    val start = at
                    while (at < text.length && text[at] !in ",]} \n\r\t") at++
                    Node.Literal(text.substring(start, at))
                }
            }
        }

        private fun string(): String {
            val start = at++
            while (text[at] != '"') at += if (text[at] == '\\') 2 else 1
            at++
            return text.substring(start, at)
        }

        private fun members(): Node {
            at++
            val members = mutableListOf<Pair<String, Node>>()
            space()
            if (text[at] == '}') {
                at++
                return Node.Members(members)
            }
            while (true) {
                space()
                val key = string()
                space()
                check(text[at++] == ':') { "':' expected at ${at - 1}" }
                members += key to value()
                space()
                when (text[at++]) {
                    ','  -> continue
                    '}'  -> return Node.Members(members)
                    else -> error("',' or '}' expected at ${at - 1}")
                }
            }
        }

        private fun items(): Node {
            at++
            val items = mutableListOf<Node>()
            space()
            if (text[at] == ']') {
                at++
                return Node.Items(items)
            }
            while (true) {
                items += value()
                space()
                when (text[at++]) {
                    ','  -> continue
                    ']'  -> return Node.Items(items)
                    else -> error("',' or ']' expected at ${at - 1}")
                }
            }
        }
    }

    /** Writes [node] the way `org.json` does, with [indent] spaces per level, or on one line for 0. */
    private fun render(node: Node, indent: Int, sorted: Boolean, depth: Int = 0): String {
        fun pad(level: Int) = if (indent == 0) "" else "\n" + " ".repeat(indent * level)
        return when (node) {
            is Node.Literal -> node.text
            is Node.Items   ->
                if (node.items.isEmpty()) "[]"
                else node.items.joinToString(",", "[", pad(depth) + "]") { pad(depth + 1) + render(it, indent, sorted, depth + 1) }

            is Node.Members ->
                if (node.members.isEmpty()) "{}"
                else (if (sorted) node.members.sortedBy { it.first } else node.members)
                    .joinToString(",", "{", pad(depth) + "}") { (key, value) ->
                        pad(depth + 1) + key + (if (indent == 0) ":" else ": ") + render(value, indent, sorted, depth + 1)
                    }
        }
    }
}

/**
 * Runs [block] with [zoneId] as the system time zone and [locale] as the default locale, and puts
 * both back afterwards. [SystemTimeZone] keeps the zone it read for a while, so it is told too.
 */
internal fun <T> inZone(zoneId: String, locale: Locale = Locale.US, block: () -> T): T {
    val oldZone = TimeZone.getDefault()
    val oldLocale = Locale.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
    Locale.setDefault(locale)
    SystemTimeZone.invalidate()
    try {
        return block()
    } finally {
        TimeZone.setDefault(oldZone)
        Locale.setDefault(oldLocale)
        SystemTimeZone.invalidate()
    }
}
