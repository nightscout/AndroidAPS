package info.nightscout.androidaps.plugins.general.tidepool.utils

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RateLimit @Inject constructor(
    val aapsLogger: AAPSLogger
) {

    private val rateLimits = HashMap<String, Long>()

    // return true if below rate limit
    @Synchronized
    fun rateLimit(name: String, seconds: Int): Boolean {
        // check if over limit
        rateLimits[name]?.let {
            if (DateUtil.now() - it < T.secs(seconds.toLong()).msecs()) {
                aapsLogger.debug(LTag.TIDEPOOL, "$name rate limited: $seconds seconds")
                return false
            }
        }
        // not over limit
        rateLimits[name] = DateUtil.now()
        return true
    }
}

