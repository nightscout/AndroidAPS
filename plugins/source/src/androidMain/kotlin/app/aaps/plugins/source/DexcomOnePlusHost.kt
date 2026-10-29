package app.aaps.plugins.source

import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.plugins.dexcomoneplus.OnePlusCgmDriver
import app.aaps.plugins.dexcomoneplus.OnePlusGlucoseWatcher
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScanner

/**
 * Everything [DexcomOnePlusPlugin] needs from the phone it runs on.
 *
 * The plugin keeps the sensor slots, the pre-soak rules, the promotion gates and the ingest path.
 * What needs an Android `Context` is asked here: the driver registry, how the three setup screens
 * are opened, the foreground service and the status-bar notification. Each of those is named one
 * by one, the same way `OnePlusPlatform` names what the driver needs, so the plugin never has to
 * know what a `Context` is and the tests can replace the host with a fake.
 *
 * ⚠️ Nothing here may answer with something that pretends to work. A CGM plugin that is switched on
 * and quietly never produces a reading is read by the dosing engine as "no glucose", which is not
 * the same thing as a reading.
 */
interface DexcomOnePlusHost {

    /**
     * Give [driver] what it needs from this phone, before anything else is asked of it.
     *
     * This is `OnePlusCgmDriver.attach` with the platform filled in by the host: a key-value file,
     * an LE scanner, a GATT stack and a wake lock.
     */
    fun attachPlatform(driver: OnePlusCgmDriver)

    /**
     * The private key-value store of one sensor slot: its identity, its pairing code, its key and
     * the marks that let a restart pick the sensor up again.
     *
     * @param namespace null for the slot that feeds the loop, [stagingNamespace] for the pre-soak.
     */
    fun slotStore(namespace: String?): OnePlusSensorStore

    /** Key-value file namespace of the pre-soak slot, so both slots keep their own identity. */
    val stagingNamespace: String

    /** The driver feeding the loop. */
    fun productionDriver(): OnePlusCgmDriver

    /** The second, independent driver for a pre-soak sensor. */
    fun stagingDriver(): OnePlusCgmDriver

    /**
     * The driver feeding the loop if it was already built, else null. Unlike [productionDriver] this
     * never builds one, so a stop does not make a driver only to shut it down.
     */
    fun existingProductionDriver(): OnePlusCgmDriver?

    /** The pre-soak driver if it was already built, else null. See [existingProductionDriver]. */
    fun existingStagingDriver(): OnePlusCgmDriver?

    /** The driver feeding the loop, with [watcher] attached. */
    fun attachProductionDriver(watcher: OnePlusGlucoseWatcher): OnePlusCgmDriver

    /**
     * Hand the pre-soak driver the production role, keeping its live link.
     *
     * @return the driver that has just been retired, for the caller to shut down, or null when
     *   there was no pre-soak driver to promote.
     */
    fun promoteStagingInstance(): OnePlusCgmDriver?

    /**
     * Ask for, or give back, the privilege that keeps a Bluetooth session alive in the background:
     * the `connectedDevice` foreground service.
     */
    fun setSessionServiceWanted(wanted: Boolean)

    /** Show the live warm-up / reconnect state where the user can see it. */
    fun showWarmup(state: OnePlusWarmupState)

    /** Take the warm-up display down. */
    fun cancelWarmupNotification()

    /** Tell the user about a problem that happened while they were not looking. */
    fun alert(message: String)

    /**
     * A scanner for the setup screens to drive themselves, separate from the driver's own.
     *
     * The start screen lists what is in range so the user can pick their sensor, which is a
     * different job from the driver's pre-connect wait for one known MAC.
     */
    fun screenScanner(): OnePlusBleScanner

    /**
     * The entries that open this plugin's own screens, for the settings screen.
     *
     * @param plugin the plugin these screens act on. It is passed in rather than injected because
     *   the plugin builds the host, so a host that asked for the plugin would be a loop.
     */
    fun screenItems(plugin: DexcomOnePlusPlugin): List<PreferenceItem>
}
