package app.aaps.wear.data

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression test for GitHub issue #4870: external (follower) data was delivered to the watch
 * but never displayed because the `key_switch_external` preference was dropped during the
 * V4 AndroidX/DataStore migration.
 *
 * A single broadcasting follower running an AAPSClient2 build tags its data with dataset=2,
 * which lands in the EXT2 slot (index 2). A watchface that only shows the EXT1 section then
 * displays nothing. With the switch enabled the two external slots swap, so the dataset=2
 * follower is rendered in the EXT1 view.
 */
class ComplicationDataExternalSwitchTest {

    @Test fun bgDataArrayWithoutSwitchKeepsNaturalOrder() {
        val data = ComplicationData()

        val bg = data.bgDataArray(switchExternal = false)

        assertThat(bg[0].dataset).isEqualTo(0) // primary (paired phone)
        assertThat(bg[1].dataset).isEqualTo(1) // EXT1 (AAPSClient1 follower)
        assertThat(bg[2].dataset).isEqualTo(2) // EXT2 (AAPSClient2 follower)
    }

    @Test fun bgDataArrayWithSwitchSwapsExternalSlots() {
        // GIVEN a lone follower (AAPSClient2 -> dataset 2) carrying recognizable BG
        val data = ComplicationData().let { it.copy(bgData2 = it.bgData2.copy(sgvString = "153")) }

        // WHEN switch_external is enabled
        val bg = data.bgDataArray(switchExternal = true)

        // THEN the primary is untouched and the dataset-2 follower now occupies the EXT1 slot
        assertThat(bg[0].dataset).isEqualTo(0)
        assertThat(bg[1].dataset).isEqualTo(2)
        assertThat(bg[1].sgvString).isEqualTo("153")
        assertThat(bg[2].dataset).isEqualTo(1)
    }

    @Test fun statusDataArrayWithoutSwitchKeepsNaturalOrder() {
        val data = ComplicationData()

        val status = data.statusDataArray(switchExternal = false)

        assertThat(status[0].dataset).isEqualTo(0)
        assertThat(status[1].dataset).isEqualTo(1)
        assertThat(status[2].dataset).isEqualTo(2)
    }

    @Test fun statusDataArrayWithSwitchSwapsExternalSlots() {
        // GIVEN a lone follower (AAPSClient2 -> dataset 2) carrying recognizable status
        val data = ComplicationData().let { it.copy(statusData2 = it.statusData2.copy(iobSum = "1.23")) }

        // WHEN switch_external is enabled
        val status = data.statusDataArray(switchExternal = true)

        // THEN the primary is untouched and the dataset-2 follower now occupies the EXT1 slot
        assertThat(status[0].dataset).isEqualTo(0)
        assertThat(status[1].dataset).isEqualTo(2)
        assertThat(status[1].iobSum).isEqualTo("1.23")
        assertThat(status[2].dataset).isEqualTo(1)
    }
}
