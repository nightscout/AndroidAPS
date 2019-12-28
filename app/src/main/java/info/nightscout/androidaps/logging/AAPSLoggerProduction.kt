package info.nightscout.androidaps.logging

import org.slf4j.LoggerFactory

/**
 * Created by adrian on 2019-12-27.
 */

class AAPSLoggerProduction : AAPSLogger {

    override fun debug(tag: LTag, message: String) {
        if (L.isEnabled(tag.tag)) {
            LoggerFactory.getLogger(tag.tag).debug(message)

        }
    }

    override fun info(tag: LTag, message: String) {
        if (L.isEnabled(tag.tag)) {
            LoggerFactory.getLogger(tag.tag).info(message)

        }
    }

    override fun error(tag: LTag, message: String) {
        if (L.isEnabled(tag.tag)) {
            LoggerFactory.getLogger(tag.tag).error(message)

        }
    }

    override fun error(tag: LTag, message: String, throwable: Throwable) {
        if (L.isEnabled(tag.tag)) {
            LoggerFactory.getLogger(tag.tag).error(message, throwable)

        }
    }
}