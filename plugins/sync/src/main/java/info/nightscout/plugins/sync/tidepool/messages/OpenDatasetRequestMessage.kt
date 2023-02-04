package info.nightscout.plugins.sync.tidepool.messages

import com.google.gson.annotations.Expose
import info.nightscout.interfaces.Config
import info.nightscout.plugins.sync.tidepool.comm.TidepoolUploader
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
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
