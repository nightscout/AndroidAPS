package app.aaps.plugins.constraints.versionChecker

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AllowedVersionsTest {

    private fun generateSupportedVersions(): String =
        "{\n" +
            "  \"Latest versions\":\"\",\n" +
            "  \"30\": \"3.3.1\",\n" +
            "  \"31\": \"3.3.1\",\n" +
            "  \"32\": \"3.3.1\",\n" +
            "  \"33\": \"3.3.1\",\n" +
            "  \"34\": \"3.3.1\",\n" +
            "  \"35\": \"3.3.1\",\n" +
            "  \"36\": \"3.3.1\",\n" +
            "  \"37\": \"3.3.1\",\n" +
            "  \"38\": \"3.3.1\",\n" +
            "  \"Expire dates\": \"\",\n" +
            "  \"3.3.0\": \"2025-05-31\"\n" +
            "}"

    @Test
    fun generateSupportedVersionsTest() {
        val definition = JSONObject(generateSupportedVersions())
        assertThat(AllowedVersions.findByApi(definition, 0)).isNull()
        assertThat(AllowedVersions.findByApi(definition, 30)).isEqualTo("3.3.1")
    }

    @Test
    fun findByVersionTest() {
        val definition = JSONObject(generateSupportedVersions())
        assertThat(AllowedVersions.findByApi(definition, 0)).isNull()
        assertThat(AllowedVersions.findByApi(definition, 30)).isEqualTo("3.3.1")
        assertThat(AllowedVersions.findByVersion(definition, "3.2.9")).isNull()
        assertThat(AllowedVersions.findByVersion(definition, "3.3.0")).isEqualTo("2025-05-31")
    }

    @Suppress("SpellCheckingInspection")
    @Test
    fun endDateToMilliseconds() {
        val definition = JSONObject(generateSupportedVersions())
        val endDate = AllowedVersions.endDateToMilliseconds(AllowedVersions.findByVersion(definition, "3.3.0") ?: "") ?: 0L
        val dateTime = LocalDate.ofInstant(Instant.ofEpochMilli(endDate), ZoneId.systemDefault())
        assertThat(dateTime.year).isEqualTo(2025)
        assertThat(dateTime.monthValue).isEqualTo(5)
        assertThat(dateTime.dayOfMonth).isEqualTo(31)

        assertThat(AllowedVersions.endDateToMilliseconds("abdef")).isNull()
    }
}
