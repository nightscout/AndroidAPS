package app.aaps.plugins.sync.garmin

import com.garmin.android.connectiq.IQDevice

data class GarminDevice(
    val client: GarminClient,
    val id: Long,
    var name: String) {

    constructor(client: GarminClient, iqDevice: IQDevice): this(
        client,
        iqDevice.deviceIdentifier,
        iqDevice.friendlyName) {}

    override fun toString(): String = "D[$name/$id]"

    fun toIQDevice() = IQDevice(id, name)

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