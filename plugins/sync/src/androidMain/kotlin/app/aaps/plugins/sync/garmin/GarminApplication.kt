package app.aaps.plugins.sync.garmin

data class GarminApplication(
    val device: GarminDevice,
    val id: String,
    val name: String?) {

    val client get() = device.client

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GarminApplication

        if (client != other.client) return false
        if (device != other.device) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = client.hashCode()
        result = 31 * result + device.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }

    override fun toString() = "A[$device:$id:$name]"
}

