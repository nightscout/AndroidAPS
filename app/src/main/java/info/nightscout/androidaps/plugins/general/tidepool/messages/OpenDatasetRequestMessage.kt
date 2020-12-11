package info.nightscout.androidaps.plugins.general.tidepool.messages

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import java.util.*

class OpenDatasetRequestMessage (val serialNumber : String): BaseMessage() {

    @Expose
    var deviceId: String = TidepoolUploader.PUMP_TYPE + ":" + serialNumber
    @Expose
    var time: String = DateUtil.toISOAsUTC(DateUtil.now())
    @Expose
    var timezoneOffset = (DateUtil.getTimeZoneOffsetMs() / T.mins(1).msecs()).toInt()
    @Expose
    var type = "upload"
    //public String byUser;
    @Expose
    var client = ClientInfo()
    @Expose
    var computerTime: String = DateUtil.toISONoZone(DateUtil.now())
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
    var version = BuildConfig.VERSION_NAME

    inner class ClientInfo {
        @Expose
        val name = BuildConfig.APPLICATION_ID
        @Expose
        val version = TidepoolUploader.VERSION
    }

    inner class Deduplicator {
        @Expose
        val name = "org.tidepool.deduplicator.dataset.delete.origin"
    }

}
