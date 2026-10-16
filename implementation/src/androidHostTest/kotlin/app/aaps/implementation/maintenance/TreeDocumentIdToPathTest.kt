package app.aaps.implementation.maintenance

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TreeDocumentIdToPathTest {

    @Test
    fun `internal storage drops the volume`() {
        assertThat(treeDocumentIdToPath("primary:Documents/AAPS")).isEqualTo("Documents/AAPS")
    }

    @Test
    fun `root of internal storage is shown as a slash`() {
        assertThat(treeDocumentIdToPath("primary:")).isEqualTo("/")
    }

    @Test
    fun `sd card keeps the volume so the user knows which card`() {
        assertThat(treeDocumentIdToPath("1234-5678:AAPS")).isEqualTo("1234-5678:AAPS")
    }

    @Test
    fun `id without a volume is shown as it is`() {
        assertThat(treeDocumentIdToPath("AAPS")).isEqualTo("AAPS")
    }
}
