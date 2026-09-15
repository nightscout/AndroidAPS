package app.aaps.pump.common.utils

import app.aaps.core.utils.StringUtil
import app.aaps.core.utils.formatUS
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.Locale

internal class StringUtilTest {

    private val originalLocale: Locale = Locale.getDefault()

    @AfterEach fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test fun fromBytes() {
        assertThat(StringUtil.fromBytes(null)).isEqualTo("null array")
        assertThat(StringUtil.fromBytes(byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte()))).isEqualTo("abc")
    }

    @Test fun appendToStringBuilder() {
        val sb = StringBuilder()
        StringUtil.appendToStringBuilder(sb, "a", ",")
        assertThat(sb.toString()).isEqualTo("a")
        StringUtil.appendToStringBuilder(sb, "b", ",")
        assertThat(sb.toString()).isEqualTo("a,b")
    }

    @Test fun getFormattedValueUS() {
        assertThat(3.2.formatUS(3)).isEqualTo("3.200")
        assertThat(3.2.formatUS(0)).isEqualTo("3")
        assertThat(1234.5.formatUS(1)).isEqualTo("1234.5") // no grouping separator
    }

    /**
     * The text goes into pump history and log lines, so it must always use a dot, whatever the
     * device locale is. The old code formatted with the default locale and then replaced a comma
     * with a dot, which did nothing on a locale that uses another separator.
     */
    @Test fun formatUsesDotInEveryLocale() {
        for (tag in listOf("en", "de-DE", "cs-CZ", "fr-FR", "ar-SA")) {
            Locale.setDefault(Locale.forLanguageTag(tag))
            assertThat(3.2.formatUS(3)).isEqualTo("3.200")
            assertThat(1234.5.formatUS(1)).isEqualTo("1234.5")
        }
    }

    @Test fun getLeadingZero() {
        assertThat(StringUtil.getLeadingZero(5, 3)).isEqualTo("005")
    }

    @Test fun getStringInLength() {
        assertThat(StringUtil.getStringInLength("a", 3)).isEqualTo("a  ")
        assertThat(StringUtil.getStringInLength("abcdef", 3)).isEqualTo("abc")
    }

    @Test fun splitString() {
        assertThat(StringUtil.splitString("abcdefgh", 2)[0]).isEqualTo("ab")
        assertThat(StringUtil.splitString("abcdefghi", 2)[4]).isEqualTo("i")
    }
}