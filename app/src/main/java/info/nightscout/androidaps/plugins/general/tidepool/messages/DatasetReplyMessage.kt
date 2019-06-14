package info.nightscout.androidaps.plugins.general.tidepool.messages


class DatasetReplyMessage {

    internal var data: Data? = null

    // openDataSet and others return this in the root of the json reply it seems
    internal var id: String? = null
    internal var uploadId: String? = null

    inner class Data {
        internal var createdTime: String? = null
        internal var deviceId: String? = null
        internal var id: String? = null
        internal var time: String? = null
        internal var timezone: String? = null
        internal var timezoneOffset: Int = 0
        internal var type: String? = null
        internal var uploadId: String? = null
        internal var client: Client? = null
        internal var computerTime: String? = null
        internal var dataSetType: String? = null
        internal var deviceManufacturers: List<String>? = null
        internal var deviceModel: String? = null
        internal var deviceSerialNumber: String? = null
        internal var deviceTags: List<String>? = null
        internal var timeProcessing: String? = null
        internal var version: String? = null
        // meta
    }

    inner class Client {
        internal var name: String? = null
        internal var version: String? = null

    }

    fun getUploadId(): String? {
        return data?.uploadId ?: uploadId
    }
}
