package app.aaps.implementation.profile

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * Covers [ProfileStoreObject] JSON accessors: with/getData, getStartDate (created_at / fallback),
 * getDefaultProfileName and getProfileList. The pureProfile conversion (getSpecificProfile) is out of scope.
 */
class ProfileStoreObjectTest : TestBase() {

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var config: Config
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var notificationManager: NotificationManager
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var dateUtil: DateUtil

    private fun store(json: String): ProfileStore =
        ProfileStoreObject(aapsLogger, activePlugin, config, rh, notificationManager, hardLimits, dateUtil)
            .with(JSONObject(json))

    @Test
    fun getData_returnsWrappedJson() {
        assertThat(store("""{"defaultProfile":"D","store":{"D":{}}}""").getData().getString("defaultProfile")).isEqualTo("D")
    }

    @Test
    fun getProfileList_returnsStoreKeys() {
        val list = store("""{"store":{"D":{},"E":{}}}""").getProfileList().map { it.toString() }
        assertThat(list).containsExactly("D", "E")
    }

    @Test
    fun getProfileList_emptyWhenNoStore() {
        assertThat(store("""{}""").getProfileList()).isEmpty()
    }

    @Test
    fun getDefaultProfileName_returnedWhenStorePresent() {
        assertThat(store("""{"defaultProfile":"D","store":{"D":{}}}""").getDefaultProfileName()).isEqualTo("D")
    }

    @Test
    fun getDefaultProfileName_nullWhenEmpty() {
        assertThat(store("""{"store":{"D":{}}}""").getDefaultProfileName()).isNull()
    }

    @Test
    fun getDefaultProfileName_nullWhenNoStore() {
        assertThat(store("""{"defaultProfile":"D"}""").getDefaultProfileName()).isNull()
    }

    @Test
    fun getStartDate_parsesCreatedAt() {
        whenever(dateUtil.fromISODateString("2023-01-01T00:00:00Z")).thenReturn(1_672_531_200_000L)
        assertThat(store("""{"created_at":"2023-01-01T00:00:00Z","store":{}}""").getStartDate())
            .isEqualTo(1_672_531_200_000L)
    }

    @Test
    fun getStartDate_zeroWhenMissing() {
        assertThat(store("""{"store":{}}""").getStartDate()).isEqualTo(0L)
    }
}
