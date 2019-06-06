package info.nightscout.androidaps.plugins.general.tidepool.messages

import com.google.gson.annotations.Expose
import info.nightscout.androidaps.BuildConfig
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.source.SourceNSClientPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import java.util.*

class OpenDatasetRequestMessage : BaseMessage() {

    @Expose
    var deviceId: String? = null
    @Expose
    var time = DateUtil.toISOAsUTC(DateUtil.now())
    @Expose
    var timezoneOffset = (DateUtil.getTimeZoneOffsetMs() / T.mins(1).msecs()).toInt()
    @Expose
    var type = "upload"
    //public String byUser;
    @Expose
    var client = ClientInfo()
    @Expose
    var computerTime = DateUtil.toISONoZone(DateUtil.now())
    @Expose
    var dataSetType = UPLOAD_TYPE  // omit for "normal"
    @Expose
    var deviceManufacturers = arrayOf(((ConfigBuilderPlugin.getPlugin().activeBgSource ?: SourceNSClientPlugin.getPlugin()) as PluginBase).name)
    @Expose
    var deviceModel = ((ConfigBuilderPlugin.getPlugin().activeBgSource ?: SourceNSClientPlugin.getPlugin()) as PluginBase).name
    @Expose
    var deviceTags = arrayOf("bgm", "cgm", "insulin-pump")
    @Expose
    var deduplicator = Deduplicator()
    @Expose
    var timeProcessing = "none"
    @Expose
    var timezone = TimeZone.getDefault().id
    @Expose
    var version = BuildConfig.VERSION_NAME

    inner class ClientInfo {
        @Expose
        val name = BuildConfig.APPLICATION_ID
        @Expose
        val version = BuildConfig.VERSION_NAME
    }

    inner class Deduplicator {
        @Expose
        val name = "org.tidepool.deduplicator.dataset.delete.origin"
    }

    companion object {
        internal val UPLOAD_TYPE = "continuous"
    }

}
