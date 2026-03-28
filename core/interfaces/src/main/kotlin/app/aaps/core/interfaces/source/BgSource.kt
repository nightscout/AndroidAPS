package app.aaps.core.interfaces.source

/**
 * Interface for blood glucose data source plugins (CGM/FGM sensors).
 *
 * BG source plugins receive glucose readings from external CGM apps or devices
 * (e.g., Dexcom, Libre, xDrip+) and store them in the database as [GlucoseValue] records.
 *
 * ## Data Reception
 * Most BG sources receive data via Android BroadcastReceivers or content provider queries.
 * The received glucose values are stored to the database via [PersistenceLayer],
 * which triggers [EventNewBgReading] on the [RxBus] and initiates a new loop cycle.
 *
 * ## Implementations
 * - **DexcomPlugin** — Native Dexcom app integration (G6, G7)
 * - **NSClientSourcePlugin** — Nightscout as BG source
 * - **XdripSourcePlugin** — xDrip+ integration
 * - **LibreLinkSourcePlugin** — FreeStyle Libre integration
 * - **GlimpPlugin** — Glimp app integration
 *
 * @see app.aaps.core.data.model.GV
 * @see app.aaps.core.interfaces.plugin.ActivePlugin.activeBgSource
 */
interface BgSource {

    /**
     *  Does bg source support advanced filtering ? Currently Dexcom native mode only
     *
     *  @return true if supported
     */
    fun advancedFilteringSupported(): Boolean = false

    /**
     *  Sensor battery level in %
     *
     *  -1 if not supported
     */
    val sensorBatteryLevel: Int
        get() = -1
}