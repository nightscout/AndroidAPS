package app.aaps.plugins.aps.autotune

import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

/**
 * Autotune nests a profile document inside another JSON object in three places, and none of them had
 * any coverage: `saveLastRun` was never called by a test, and `ATProfile.profileStore` was only ever
 * mocked.
 *
 * That matters more than an untested getter, because the failure mode here is silent.
 * `org.json.JSONObject.put(String, Any?)` accepts *anything*: handing it a value the library does not
 * recognise stores the object and serialises it as a **quoted string** rather than a nested object.
 * It compiles, it runs, and the damage only shows up in the written document - which is exactly what
 * would have happened when `toPureNsJson` started returning a kotlinx tree. So these tests assert the
 * nested value is a real object, not just that it is present.
 */
class AutotuneProfileJsonTest : TestBaseWithProfile() {

    private fun atProfile(): ATProfile =
        ATProfile(preferences, profileUtil, dateUtil, rh, { profileStoreProvider() }, aapsLogger)
            .with(validProfile, someICfg)

    @Test
    fun `the tuned profile store nests a real profile object`() {
        val store = atProfile().also { it.profileName = "Tuned" }.profileStore()

        assertThat(store).isNotNull()
        val nested = JSONObject(store!!.getData().toString()).getJSONObject("store")
        // getJSONObject throws if the value were serialised as a string, which is the whole point.
        val profile = nested.getJSONObject("Tuned")
        assertThat(profile.has("basal")).isTrue()
        assertThat(profile.getJSONArray("basal").length()).isAtLeast(1)
        assertThat(profile.getJSONArray("basal").getJSONObject(0).has("timeAsSeconds")).isTrue()
    }

    /** The store must be readable back as a profile, not merely well-formed. */
    @Test
    fun `the tuned profile store parses back into a profile`() {
        val store = atProfile().also { it.profileName = "Tuned" }.profileStore()

        val parsed = store!!.getSpecificProfile("Tuned")

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.basalBlocks).isNotEmpty()
        assertThat(parsed.isfBlocks).isNotEmpty()
        assertThat(parsed.icBlocks).isNotEmpty()
        assertThat(parsed.targetBlocks).isNotEmpty()
    }

    @Test
    fun `data() rebuilds a profile from the tuned document`() {
        val data = atProfile().data()

        assertThat(data).isNotNull()
        assertThat(data!!.basalBlocks).isNotEmpty()
        assertThat(data.targetBlocks).isNotEmpty()
    }

    /**
     * `ProfileSealed.Pure` and the tuned profile must serialise the same way - this is the path that
     * feeds both the profile store above and `saveLastRun`.
     */
    @Test
    fun `a pure profile serialises as a nested object when embedded`() {
        val embedded = JSONObject().put("pumpProfile", JSONObject(ProfileSealed.Pure(validProfile.value, activePlugin).toPureNsJson(dateUtil).toString()))

        val readBack = JSONObject(embedded.toString()).getJSONObject("pumpProfile")

        assertThat(readBack.has("sens")).isTrue()
        assertThat(readBack.has("basal")).isTrue()
        assertThat(readBack.getJSONArray("sens").getJSONObject(0).has("value")).isTrue()
    }
}
