package app.aaps.plugins.sync.nsclientV3.extensions

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Covers [toRunningConfiguration]: parse of the NS settings `runningConfig` block + the null fall-throughs. */
class SettingsExtensionTest {

    @Test
    fun parsesRunningConfigFields() {
        val json = """{"schemaVersion":1,"runningConfig":{"pump":"DanaRS","version":"3.0","isFakingTempsByExtendedBoluses":true}}"""
        val config = json.toRunningConfiguration()
        assertThat(config).isNotNull()
        assertThat(config!!.pump).isEqualTo("DanaRS")
        assertThat(config.version).isEqualTo("3.0")
        assertThat(config.isFakingTempsByExtendedBoluses).isTrue()
    }

    @Test
    fun parsesNestedActiveSceneAndSyncedPrefs() {
        val json = """
            {"runningConfig":{
                "syncedPrefs":{"key_a":"1","key_b":"true"},
                "activeScene":{"sceneId":"exercise","activatedAt":1000,"durationMs":3600000}
            }}
        """.trimIndent()
        val config = json.toRunningConfiguration()
        assertThat(config).isNotNull()
        assertThat(config!!.syncedPrefs).containsEntry("key_a", "1")
        assertThat(config.activeScene?.sceneId).isEqualTo("exercise")
        assertThat(config.activeScene?.durationMs).isEqualTo(3600000L)
    }

    @Test
    fun ignoresUnknownKeys() {
        val json = """{"runningConfig":{"pump":"X","somethingNew":42}}"""
        assertThat(json.toRunningConfiguration()?.pump).isEqualTo("X")
    }

    @Test
    fun returnsNullWhenRunningConfigMissing() {
        assertThat("""{"schemaVersion":1}""".toRunningConfiguration()).isNull()
    }

    @Test
    fun returnsNullWhenRunningConfigIsNotAnObject() {
        assertThat("""{"runningConfig":123}""".toRunningConfiguration()).isNull()
    }

    @Test
    fun returnsNullForMalformedJson() {
        assertThat("not json {".toRunningConfiguration()).isNull()
    }
}
