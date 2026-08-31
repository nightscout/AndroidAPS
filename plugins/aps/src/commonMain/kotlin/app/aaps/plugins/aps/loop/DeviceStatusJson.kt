package app.aaps.plugins.aps.loop

import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.pump.PumpEnactResult

/**
 * The two fragments of the uploaded device status whose **exact rendering** Nightscout has always
 * received, and which must not change.
 *
 * This is the only reason `LoopPlugin` is not entirely platform-neutral. Everything else in
 * `buildAndStoreDeviceStatus` is already kotlinx serialization; these two are still `org.json`, and
 * that is deliberate:
 *
 *  - **Numbers render differently.** `org.json` prints a whole Double as a bare integer (`10.0` becomes
 *    `10`) and negative zero as `-0`; kotlinx prints `10.0`. `DS.iob` is stored as raw JSON **text** and
 *    kotlinx keeps a parsed number's literal form when it re-serializes, so the difference survives all
 *    the way to the server. `IobTotalJsonAdapters` re-renders through `org.json` for exactly this
 *    reason - see the note there.
 *  - **[enactedRateAndDuration] is meant to throw** when the result carries neither, which happens for
 *    an SMB-only result. That has always been the behaviour and callers rely on it.
 *
 * Changing either of these is a wire-format change and needs recorded vectors first, not a refactor.
 */
interface DeviceStatusJson {

    /** The `openaps.iob` fragment, with `time` set to [atTime]. */
    fun iob(iob: IobTotal, atTime: Long): String

    /**
     * The rate and duration the pump actually enacted, as `Number` so an `Int` stays an `Int`.
     *
     * A cancelled temp reports integer `0` for both, and rendering that as `0.0` would change what is
     * uploaded.
     *
     * @throws Exception if [result] carries neither - an SMB-only result. Callers rely on this, and the
     *   concrete type is the platform JSON library's own; `LoopPluginTest` pins it.
     */
    fun enactedRateAndDuration(result: PumpEnactResult, baseBasal: Double): Pair<Number, Number>
}
