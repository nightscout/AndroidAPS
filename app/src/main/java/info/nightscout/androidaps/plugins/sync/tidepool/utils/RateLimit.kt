package info.nightscout.androidaps.plugins.sync.tidepool.utils

import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RateLimit @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil
) {

    private val rateLimits = HashMap<String, Long>()

    // return true if below rate limit
    @Synchronized
    fun rateLimit(name: String, seconds: Int): Boolean {
        // check if over limit
        rateLimits[name]?.let {
            if (dateUtil.now() - it < T.secs(seconds.toLong()).msecs()) {
                aapsLogger.debug(LTag.TIDEPOOL, "$name rate limited: $seconds seconds")
                return false
            }
        }
        // not over limit
        rateLimits[name] = dateUtil.now()
        return true
    }
}

