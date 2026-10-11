package app.aaps.plugins.sync.tidepool.messages

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import com.google.gson.annotations.Expose
import java.util.TimeZone

class OpenDatasetRequestMessage(config: Config, dateUtil: DateUtil) : BaseMessage() {

    // TidepoolUploader.startSession finds this dataset again by this id
    @Expose
    var deviceId: String = TidepoolUploader.DEVICE_NAME

    @Expose
    var time: String = dateUtil.toISOAsUTC(System.currentTimeMillis())

    @Expose
    // With DST, like the offset of every uploaded record
    var timezoneOffset = (dateUtil.getTimeZoneOffsetMsWithDST() / T.mins(1).msecs()).toInt()

    @Expose
    var type = "upload"

    //public String byUser;
    @Expose
    var client = ClientInfo(config.APPLICATION_ID, config.VERSION_NAME)

    @Expose
    var computerTime: String = dateUtil.toISONoZone(System.currentTimeMillis())

    @Expose
    var dataSetType = "continuous"

    @Expose
    var deviceManufacturers = arrayOf(TidepoolUploader.DEVICE_NAME)

    @Expose
    var deviceModel = TidepoolUploader.DEVICE_NAME

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
        @Expose val version: String
    )

    inner class Deduplicator {

        @Expose
        val name = "org.tidepool.deduplicator.dataset.delete.origin"
    }

}
