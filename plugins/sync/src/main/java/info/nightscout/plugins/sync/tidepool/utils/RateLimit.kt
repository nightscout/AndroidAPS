package info.nightscout.plugins.sync.tidepool.utils

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
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
                //aapsLogger.debug(LTag.TIDEPOOL, "$name rate limited: $seconds seconds")
                return false
            }
        }
        // not over limit
        rateLimits[name] = dateUtil.now()
        return true
    }
}

