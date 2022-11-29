package info.nightscout.plugins.sync.tidepool.messages

import com.google.gson.annotations.Expose
import info.nightscout.plugins.sync.tidepool.comm.TidepoolUploader
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import java.util.TimeZone

class OpenDatasetRequestMessage(serialNumber: String, dateUtil: DateUtil) : BaseMessage() {

    @Expose
    var deviceId: String = TidepoolUploader.PUMP_TYPE + ":" + serialNumber

    @Expose
    var time: String = dateUtil.toISOAsUTC(System.currentTimeMillis())

    @Expose
    var timezoneOffset = (dateUtil.getTimeZoneOffsetMs() / T.mins(1).msecs()).toInt()

    @Expose
    var type = "upload"

    //public String byUser;
    @Expose
    var client = ClientInfo()

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
    var version = "1.0"

    inner class ClientInfo {

        @Expose
        val name = "AAPS"

        @Expose
        val version = TidepoolUploader.VERSION
    }

    inner class Deduplicator {

        @Expose
        val name = "org.tidepool.deduplicator.dataset.delete.origin"
    }

}
