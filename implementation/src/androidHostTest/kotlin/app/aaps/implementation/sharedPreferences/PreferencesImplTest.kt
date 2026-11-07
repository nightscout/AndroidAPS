package app.aaps.implementation.sharedPreferences

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.observeEffective
import app.aaps.core.keys.interfaces.observeHardenedMode
import app.aaps.implementation.maintenance.migration.FileKeyValueStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Hardened mode, as [PreferencesImpl] applies it.
 *
 * The rule is one property, `Preferences.hardenedMode`, and one key flag, `defaultedByHardenedMode`:
 * while hardened mode is on, a flagged key reads its default. These tests run the real store, so
 * they check the value the rest of the app reads, not a stub of it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesImplTest {

    /** The keys hardened mode forces, and the value each is forced to. */
    private val forcedKeys = mapOf(
        BooleanKey.NsClientAcceptCgmData to false,
        BooleanKey.NsClientAcceptProfileStore to false,
        BooleanKey.NsClientAcceptTempTarget to false,
        BooleanKey.NsClientAcceptProfileSwitch to false,
        BooleanKey.NsClientAcceptInsulin to false,
        BooleanKey.NsClientAcceptCarbs to false,
        BooleanKey.NsClientAcceptTherapyEvent to false,
        BooleanKey.NsClientAcceptRunningMode to false,
        BooleanKey.NsClientAcceptTbrEb to false,
        BooleanKey.NsClientUploadData to true,
        BooleanKey.BgSourceUploadToNs to true,
        BooleanKey.NsClient3UseWs to true
    )

    private fun preferences(client: Boolean): PreferencesImpl {
        val config = mock<Config>()
        whenever(config.APS).thenReturn(!client)
        whenever(config.AAPSCLIENT).thenReturn(client)
        whenever(config.platform).thenReturn(AppPlatform.Android)
        return PreferencesImpl(FileKeyValueStore(), { mock() }, { mock() }, { mock() }, mock(), config, mock())
    }

    /** A master outside simple mode, every forced key stored as the opposite of its forced value. */
    private fun masterWithOppositeValues(): PreferencesImpl =
        preferences(client = false).also { prefs ->
            prefs.put(BooleanKey.GeneralSimpleMode, false)
            forcedKeys.forEach { (key, forced) -> prefs.put(key, !forced) }
        }

    @Test
    fun `exactly the agreed keys are forced by hardened mode, and their default is the hardened value`() {
        val flagged = BooleanKey.entries.filter { it.defaultedByHardenedMode }
        assertThat(flagged).containsExactlyElementsIn(forcedKeys.keys)
        forcedKeys.forEach { (key, forced) -> assertThat(key.defaultValue).isEqualTo(forced) }
    }

    @Test
    fun `the switch and simple mode are not forced by hardened mode, so reading hardened mode cannot loop`() {
        assertThat(BooleanKey.HardenedMode.defaultedByHardenedMode).isFalse()
        assertThat(BooleanKey.GeneralSimpleMode.defaultedByHardenedMode).isFalse()
    }

    @Test
    fun `master with the switch on ignores the stored values of the forced keys`() {
        val prefs = masterWithOppositeValues()
        prefs.put(BooleanKey.HardenedMode, true)

        assertThat(prefs.hardenedMode).isTrue()
        forcedKeys.forEach { (key, forced) -> assertThat(prefs.get(key)).isEqualTo(forced) }
    }

    @Test
    fun `master with the switch off reads the stored values`() {
        val prefs = masterWithOppositeValues()
        prefs.put(BooleanKey.HardenedMode, false)

        assertThat(prefs.hardenedMode).isFalse()
        // TbrEb is an engineering-mode key, so outside engineering mode it reads its default anyway.
        forcedKeys.filterKeys { it != BooleanKey.NsClientAcceptTbrEb }
            .forEach { (key, forced) -> assertThat(prefs.get(key)).isEqualTo(!forced) }
    }

    @Test
    fun `simple mode turns hardened mode on, even over a stored off`() {
        val prefs = masterWithOppositeValues()
        prefs.put(BooleanKey.HardenedMode, false)
        prefs.put(BooleanKey.GeneralSimpleMode, true)

        assertThat(prefs.get(BooleanKey.HardenedMode)).isTrue()
        assertThat(prefs.hardenedMode).isTrue()
        assertThat(prefs.get(BooleanKey.NsClientAcceptCarbs)).isFalse()
    }

    @Test
    fun `the published value of the switch is the stored one, so the client can apply simple mode itself`() {
        val prefs = preferences(client = false)
        prefs.put(BooleanKey.GeneralSimpleMode, true)
        prefs.put(BooleanKey.HardenedMode, false)

        assertThat(prefs.get(BooleanKey.HardenedMode, forSync = true)).isFalse()
    }

    @Test
    fun `an unset switch is published as on in simple mode and off outside it`() {
        val prefs = preferences(client = false)
        prefs.put(BooleanKey.GeneralSimpleMode, true)
        assertThat(prefs.get(BooleanKey.HardenedMode, forSync = true)).isTrue()

        prefs.put(BooleanKey.GeneralSimpleMode, false)
        assertThat(prefs.get(BooleanKey.HardenedMode, forSync = true)).isFalse()
    }

    @Test
    fun `a paired client follows the master's switch`() {
        val prefs = preferences(client = true)
        prefs.put(BooleanKey.GeneralSimpleMode, false)
        prefs.put(BooleanKey.NsClient3UseWs, false)
        prefs.put(StringNonKey.NsClientControlClientId, "client-1")
        prefs.putRemote(BooleanKey.HardenedMode, true, 1L)

        assertThat(prefs.hardenedMode).isTrue()
        assertThat(prefs.get(BooleanKey.NsClient3UseWs)).isTrue()
    }

    @Test
    fun `an unpaired client is a viewer and keeps its own WebSocket choice outside simple mode`() {
        val prefs = preferences(client = true)
        prefs.put(BooleanKey.GeneralSimpleMode, false)
        prefs.put(BooleanKey.NsClient3UseWs, false)
        prefs.putRemote(BooleanKey.HardenedMode, true, 1L)

        assertThat(prefs.hardenedMode).isFalse()
        assertThat(prefs.get(BooleanKey.NsClient3UseWs)).isFalse()
    }

    @Test
    fun `simple mode keeps the WebSocket on for every phone, an unpaired client included`() {
        val prefs = preferences(client = true)
        prefs.put(BooleanKey.NsClient3UseWs, false)
        prefs.put(BooleanKey.GeneralSimpleMode, true)

        assertThat(prefs.hardenedMode).isFalse()
        assertThat(prefs.get(BooleanKey.NsClient3UseWs)).isTrue()
    }

    @Test
    fun `observeEffective follows a change that comes from simple mode, which observe alone misses`() = runTest {
        val prefs = preferences(client = false)
        prefs.put(BooleanKey.GeneralSimpleMode, false)
        prefs.put(BooleanKey.HardenedMode, false)
        prefs.put(BooleanKey.NsClient3UseWs, false)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            prefs.observeEffective(BooleanKey.NsClient3UseWs).collect { values += it }
        }

        prefs.put(BooleanKey.GeneralSimpleMode, true)
        prefs.put(BooleanKey.GeneralSimpleMode, false)

        assertThat(values).containsExactly(false, true, false).inOrder()
    }

    @Test
    fun `observeHardenedMode on a client follows the pairing`() = runTest {
        val prefs = preferences(client = true)
        prefs.put(BooleanKey.GeneralSimpleMode, false)
        prefs.putRemote(BooleanKey.HardenedMode, true, 1L)
        val values = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            prefs.observeHardenedMode().collect { values += it }
        }

        prefs.put(StringNonKey.NsClientControlClientId, "client-1")
        prefs.put(StringNonKey.NsClientControlClientId, "")

        assertThat(values).containsExactly(false, true, false).inOrder()
    }
}
