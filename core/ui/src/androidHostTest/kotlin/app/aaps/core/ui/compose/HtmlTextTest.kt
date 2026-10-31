package app.aaps.core.ui.compose

import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class HtmlTextTest {

    @Test fun `plain text is unchanged`() {
        assertThat("just text".htmlToAnnotatedString().text).isEqualTo("just text")
    }

    @Test fun `br becomes a newline`() {
        assertThat("a<br>b".htmlToAnnotatedString().text).isEqualTo("a\nb")
        assertThat("a<br/>b".htmlToAnnotatedString().text).isEqualTo("a\nb")
        assertThat("a<br />b".htmlToAnnotatedString().text).isEqualTo("a\nb")
        assertThat("a<BR>b".htmlToAnnotatedString().text).isEqualTo("a\nb")
    }

    @Test fun `b marks the enclosed text bold and nothing else`() {
        val result = "no<b>yes</b>no".htmlToAnnotatedString()
        assertThat(result.text).isEqualTo("noyesno")
        val spans = result.spanStyles
        assertThat(spans).hasSize(1)
        assertThat(spans[0].item.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(spans[0].start).isEqualTo(2)
        assertThat(spans[0].end).isEqualTo(5)
    }

    @Test fun `strong is treated as bold`() {
        val result = "<strong>x</strong>".htmlToAnnotatedString()
        assertThat(result.text).isEqualTo("x")
        assertThat(result.spanStyles.single().item.fontWeight).isEqualTo(FontWeight.Bold)
    }

    @Test fun `entities are decoded`() {
        assertThat("a&amp;b".htmlToAnnotatedString().text).isEqualTo("a&b")
        assertThat("&lt;tag&gt;".htmlToAnnotatedString().text).isEqualTo("<tag>")
        assertThat("say &quot;hi&quot;".htmlToAnnotatedString().text).isEqualTo("say \"hi\"")
        assertThat("it&#39;s".htmlToAnnotatedString().text).isEqualTo("it's")
    }

    @Test fun `a bare ampersand survives`() {
        assertThat("a & b".htmlToAnnotatedString().text).isEqualTo("a & b")
        assertThat("a &notanentity; b".htmlToAnnotatedString().text).isEqualTo("a &notanentity; b")
    }

    @Test fun `an unknown tag is kept rather than dropped`() {
        assertThat("2 < 3".htmlToAnnotatedString().text).isEqualTo("2 < 3")
        assertThat("<unknown>x".htmlToAnnotatedString().text).isEqualTo("<unknown>x")
    }

    @Test fun `an unclosed b still ends`() {
        val result = "<b>forever".htmlToAnnotatedString()
        assertThat(result.text).isEqualTo("forever")
        assertThat(result.spanStyles.single().end).isEqualTo(7)
    }

    @Test fun `a stray closing tag is ignored`() {
        assertThat("x</b>y".htmlToAnnotatedString().text).isEqualTo("xy")
    }

    @Test fun `the real pump enact result shape renders`() {
        val result = "<b>Success</b>: false<br><b>Enacted</b>: true".htmlToAnnotatedString()
        assertThat(result.text).isEqualTo("Success: false\nEnacted: true")
        assertThat(result.spanStyles).hasSize(2)
    }
}
