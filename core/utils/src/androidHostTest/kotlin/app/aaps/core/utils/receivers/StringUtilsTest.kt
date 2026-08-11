package app.aaps.core.utils.receivers

import com.google.common.truth.Truth.assertThat
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
