package app.aaps.core.nssdk

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [throughJavaScript] is only worth something if it rounds exactly like JavaScript. The expected text is
 * what Node v24.16.0 printed for the same input, with
 * `node -e "console.log(JSON.stringify(JSON.parse(input)))"`.
 */
class JavaScriptJsonTest {

    @Test
    fun `numbers come back as JavaScript writes them`() {
        val input = """{"a":[9223372036854775807,9223372036854776000,9007199254740991,9007199254740992,9007199254740993,153722867280912,1795451746860,1000,1.5,0.1,0,-5,315360000000]}"""
        val fromNode = """{"a":[9223372036854776000,9223372036854776000,9007199254740991,9007199254740992,9007199254740992,153722867280912,1795451746860,1000,1.5,0.1,0,-5,315360000000]}"""

        assertThat(throughJavaScript(input)).isEqualTo(fromNode)
    }

    @Test
    fun `strings, booleans and null are not touched`() {
        val input = """{"s":"9223372036854775807","b":true,"n":null,"o":{"x":false}}"""

        assertThat(throughJavaScript(input)).isEqualTo(input)
    }
}
