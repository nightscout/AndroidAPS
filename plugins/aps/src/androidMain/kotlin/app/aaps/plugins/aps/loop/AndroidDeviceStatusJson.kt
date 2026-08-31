package app.aaps.plugins.aps.loop

import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.json
import app.aaps.plugins.aps.loop.extensions.json
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Keeps the two byte-sensitive fragments on `org.json`, which is what produces the rendering
 * Nightscout has always received. See [DeviceStatusJson] for why that matters.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidDeviceStatusJson @Inject constructor(
    private val dateUtil: DateUtil
) : DeviceStatusJson {

    override fun iob(iob: IobTotal, atTime: Long): String =
        iob.json(dateUtil).also { it.put("time", dateUtil.toISOString(atTime)) }.toString()

    override fun enactedRateAndDuration(result: PumpEnactResult, baseBasal: Double): Pair<Number, Number> {
        val pumpJson = result.json(baseBasal)
        // `get`, not `opt`, and no pre-check: an SMB-only result carries neither key, and callers rely on
        // the `JSONException` that comes out of here. `LoopPluginTest` pins exactly that.
        return pumpJson.get("rate") as Number to pumpJson.get("duration") as Number
    }
}
