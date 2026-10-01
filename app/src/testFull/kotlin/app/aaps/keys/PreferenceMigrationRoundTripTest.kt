package app.aaps.keys

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.PreferenceKeyResolver
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.di.metro.AppRootGraph
import app.aaps.implementation.maintenance.migration.FileKeyValueStore
import app.aaps.implementation.maintenance.migration.PreferenceMigrations
import app.aaps.shared.tests.aapsClassesOnClasspath
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Every name the preference migrations write must be a key this build actually has.
 *
 * ## Why this is in `:app`
 *
 * Most destinations are named through their key enum, so the compiler checks them. Two are not:
 * `Objectives_started_` and `Objectives_accomplished_` are written as literals, because
 * `ObjectivesLongComposedKey` lives in `:plugins:constraints` and `:implementation` cannot depend on
 * it - and moving plugin keys into `:core:keys` so a migration can name them would fill that module
 * with other modules' keys, one migration at a time.
 *
 * The price of a literal is that a rename breaks it silently: the migration keeps running, keeps
 * finding its legacy names, and writes them where nothing reads. No compile error, no failing test
 * near it. That is not hypothetical - the `OpenAPSSMBDynamicISFPlugin` migration wrote
 * `ConfigBuilder_APS_OpenAPSSMB_Enabled` while `ConfigBuilderImpl` composed `APS_OpenAPSSMBPlugin`,
 * and did nothing at all from January 2024 until somebody read it.
 *
 * Only `:app` has every module's key enum on the classpath, so only here can that be checked.
 *
 * ## If it fails
 *
 * A destination in `PreferenceMigrations` no longer matches the key that owns it. Fix the name there.
 * Do not relax this test, and do not move the key into `:core:keys` to make it reachable.
 */
class PreferenceMigrationRoundTripTest {

    private fun allKeys(): List<NonPreferenceKey> =
        aapsClassesOnClasspath(listOf(AppRootGraph::class.java))
            .filter { NonPreferenceKey::class.java.isAssignableFrom(it) && it.isEnum }
            .flatMap { it.enumConstants.orEmpty().toList() }
            .filterIsInstance<NonPreferenceKey>()

    private fun migrations(): PreferenceMigrations {
        val config = mock<Config>()
        whenever(config.AAPSCLIENT).thenReturn(false)
        whenever(config.APS).thenReturn(true)
        return PreferenceMigrations(mock<AAPSLogger>(), config, mock<PersistenceLayer>(), mock<DateUtil>(), mock<ProfileUtil>())
    }

    /**
     * One legacy name per rename, spelled the way the version that wrote it spelled it.
     *
     * Verified against tag `3.3.2.1`: `ProfilePlugin.storeSettings` wrote `LocalProfile_<i>_<field>`,
     * `ConfigBuilderPlugin` wrote `"ConfigBuilder_" + type.name + "_" + p.javaClass.simpleName + "_Enabled"`,
     * and `Objective` wrote `"Objectives_" + spName + "_started"`.
     */
    private val legacyStore = mapOf<String, Any?>(
        "Monitor_Overview_total" to 42L,
        "Monitor_Overview_resumed" to 42L,
        "Monitor_Overview_start" to 42L,
        "Objectives_config_started" to 42L,
        "Objectives_config_accomplished" to 42L,
        "ConfigBuilder_PUMP_VirtualPumpPlugin_Enabled" to true,
        "appwidget_use_black_0" to true,
        "LocalProfile_0_name" to "Adult",
        "LocalProfile_0_mgdl" to false,
        "LocalProfile_0_isf" to SCHEDULE,
        "LocalProfile_0_ic" to SCHEDULE,
        "LocalProfile_0_basal" to SCHEDULE,
        "LocalProfile_0_targetlow" to SCHEDULE,
        "LocalProfile_0_targethigh" to SCHEDULE
    )

    @Test
    fun `every name the migrations write resolves to a key this build has`() = runTest {
        val keys = allKeys()
        check(keys.size > 200) { "Found only ${keys.size} keys - the classpath scan broke" }
        val resolver = PreferenceKeyResolver(keys)

        val store = FileKeyValueStore(legacyStore)
        migrations().migrate(store)

        // Anything in the store now that was not in the file is something a migration wrote.
        val written = store.getAll().keys - legacyStore.keys
        check(written.isNotEmpty()) { "The migrations wrote nothing - the sample store no longer matches any of them" }

        assertThat(written.filterNot { resolver.canResolve(it) }).isEmpty()
    }

    /**
     * Every migration in the sample must still match something.
     *
     * The test above only checks what WAS written. A migration whose source pattern stopped matching
     * writes nothing at all and would pass it silently - the other half of how a migration dies.
     */
    @Test
    fun `every legacy name in the sample is consumed`() = runTest {
        val store = FileKeyValueStore(legacyStore)

        migrations().migrate(store)

        // `_name` is deliberately copied rather than moved: MainApp still reads it to pair a profile
        // with the DIA it was using, for the database migration in dataMigrations().
        assertThat(store.getAll().keys.intersect(legacyStore.keys)).containsExactly("LocalProfile_0_name")
    }

    private companion object {

        const val SCHEDULE = "[{\"time\":\"00:00\",\"timeAsSeconds\":0,\"value\":3}]"
    }
}
