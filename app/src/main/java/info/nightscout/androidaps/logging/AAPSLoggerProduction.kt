package info.nightscout.androidaps.logging

import org.slf4j.LoggerFactory

/**
 * Created by adrian on 2019-12-27.
 */

class AAPSLoggerProduction : AAPSLogger {

    override fun debug(message: String) {
        LoggerFactory.getLogger(LTag.CORE.tag).debug(message)
    }

    override fun debug(enable: Boolean, tag: LTag, message: String) {
        if (enable && L.isEnabled(tag.tag)) {
            LoggerFactory.getLogger(tag.tag).debug(message)
        }
    }

    override fun debug(tag: LTag, message: String) {
        if (L.isEnabled(tag.tag)) {
            LoggerFactory.getLogger(tag.tag).debug(message)
        }
    }

    override fun warn(tag: LTag, message: String) {
        if (L.isEnabled(tag.tag)) {
            LoggerFactory.getLogger(tag.tag).warn(message)
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

    override fun error(message: String) {
        LoggerFactory.getLogger(LTag.CORE.tag).error(message)
    }

    override fun error(message: String, throwable: Throwable) {
        LoggerFactory.getLogger(LTag.CORE.tag).error(message, throwable)
    }

    override fun error(tag: LTag, message: String, throwable: Throwable) {
        if (L.isEnabled(tag.tag)) {
            LoggerFactory.getLogger(tag.tag).error(message, throwable)
        }
    }
}