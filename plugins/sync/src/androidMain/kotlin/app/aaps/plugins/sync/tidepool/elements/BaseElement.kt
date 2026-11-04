package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.interfaces.utils.DateUtil
import com.google.gson.annotations.Expose

open class BaseElement(timestamp: Long, uuid: String, dateUtil: DateUtil) {

    @Expose
    var deviceTime: String = ""

    @Expose
    var time: String = ""

    @Expose
    var timezoneOffset: Int = 0

    @Expose
    var type: String? = null

    @Expose
    var origin: Origin? = null

    // The device that recorded it. Tidepool groups data by this id; `UploadChunk` fills it for every record.
    @Expose
    var deviceId: String? = null

    init {
        deviceTime = dateUtil.toISONoZone(timestamp)
        time = dateUtil.toISOAsUTC(timestamp)
        timezoneOffset = dateUtil.getTimeZoneOffsetMinutes(timestamp) // TODO
        origin = Origin(uuid)
    }

    /**
     * [id] is what the Tidepool deduplicator matches on, so it must stay stable for the same record.
     * [name], [version] and [type] say which app sent it; `UploadChunk` fills them for every record.
     */
    inner class Origin internal constructor(
        @field:Expose
        internal var id: String
    ) {

        @field:Expose
        internal var name: String? = null

        @field:Expose
        internal var version: String? = null

        @field:Expose
        internal var type: String? = null
    }
}