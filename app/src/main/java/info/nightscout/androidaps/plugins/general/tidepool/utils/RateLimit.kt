package info.nightscout.androidaps.plugins.general.tidepool.utils

import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import org.slf4j.LoggerFactory
import java.util.*

object RateLimit {

    private val rateLimits = HashMap<String, Long>()

    private val log = LoggerFactory.getLogger(L.TIDEPOOL)

    // return true if below rate limit
    @Synchronized
    fun ratelimit(name: String, seconds: Int): Boolean {
        // check if over limit
        if (rateLimits.containsKey(name) && DateUtil.now() - rateLimits.get(name)!! < T.secs(seconds.toLong()).msecs()) {
            if (L.isEnabled(L.TIDEPOOL))
                log.debug("$name rate limited: $seconds seconds")
            return false
        }
        // not over limit
        rateLimits.put(name, DateUtil.now())
        return true
    }
}

