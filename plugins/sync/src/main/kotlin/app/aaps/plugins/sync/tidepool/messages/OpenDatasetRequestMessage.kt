package app.aaps.plugins.sync.tidepool.messages

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import com.google.gson.annotations.Expose
import java.util.TimeZone

class OpenDatasetRequestMessage(config: Config, dateUtil: DateUtil) : BaseMessage() {

    @Expose
    var deviceId: String? = null

    @Expose
    var time: String = dateUtil.toISOAsUTC(System.currentTimeMillis())

    @Expose
    var timezoneOffset = (dateUtil.getTimeZoneOffsetMs() / T.mins(1).msecs()).toInt()

    @Expose
    var type = "upload"

    //public String byUser;
    @Expose
    var client = ClientInfo(config.APPLICATION_ID)

    @Expose
    var computerTime: String = dateUtil.toISONoZone(System.currentTimeMillis())

    @Expose
    var dataSetType = "continuous"

    @Expose
    var deviceManufacturers = arrayOf(TidepoolUploader.PUMP_TYPE)

    @Expose
    var deviceModel = TidepoolUploader.PUMP_TYPE

    @Expose
    var deviceTags = arrayOf("bgm", "cgm", "insulin-pump")

    @Expose
    var deduplicator = Deduplicator()

    @Expose
    var timeProcessing = "none"

    @Expose
    var timezone: String = TimeZone.getDefault().id

    @Expose
    var version = config.VERSION_NAME

    inner class ClientInfo(
        @Expose val name: String,
        @Expose val version: String = TidepoolUploader.VERSION
    )

    inner class Deduplicator {

        @Expose
        val name = "org.tidepool.deduplicator.dataset.delete.origin"
    }

}
