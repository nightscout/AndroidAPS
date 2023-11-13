package app.aaps.plugins.sync.garmin

import com.garmin.android.connectiq.IQDevice

data class GarminDevice(
    val client: GarminClient,
    val id: Long,
    var name: String,
    var status: Status = Status.UNKNOWN) {

    constructor(client: GarminClient, iqDevice: IQDevice): this(
        client,
        iqDevice.deviceIdentifier,
        iqDevice.friendlyName,
        Status.from(iqDevice.status)) {}

    enum class Status {
        NOT_PAIRED,
        NOT_CONNECTED,
        CONNECTED,
        UNKNOWN;

        companion object {
            fun from(ordinal: Int?): Status =
                values().firstOrNull { s -> s.ordinal == ordinal } ?: UNKNOWN
        }
    }


    override fun toString(): String = "D[$name/$id]"

    fun toIQDevice() = IQDevice().apply {
        deviceIdentifier = id
        friendlyName = name
        status = Status.UNKNOWN.ordinal }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GarminDevice

        if (client != other.client) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = client.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }
}