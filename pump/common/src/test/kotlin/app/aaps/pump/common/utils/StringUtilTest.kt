package app.aaps.pump.common.utils

import app.aaps.core.utils.StringUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class StringUtilTest {

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
        assertThat(StringUtil.getFormattedValueUS(3.2, 3)).isEqualTo("3.200")
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