package info.nightscout.core.utils

import com.google.common.truth.Truth.assertThat
import info.nightscout.core.utils.receivers.StringUtils
import org.junit.jupiter.api.Test

class StringUtilsTest {

    @Test fun removeSurroundingQuotesTest() {
        var compareString = "test"
        assertThat(StringUtils.removeSurroundingQuotes(compareString)).isEqualTo(compareString)
        assertThat(StringUtils.removeSurroundingQuotes("\"" + compareString + "\"")).isEqualTo(compareString)
        assertThat(StringUtils.removeSurroundingQuotes("\"" + compareString)).isEqualTo("\"" + compareString)
        compareString = """te"st"""
        assertThat(StringUtils.removeSurroundingQuotes(compareString)).isEqualTo(compareString)
        assertThat(StringUtils.removeSurroundingQuotes("\"" + compareString + "\"")).isEqualTo(compareString)
        assertThat(StringUtils.removeSurroundingQuotes("\"" + compareString)).isEqualTo("\"" + compareString)
    }
}
