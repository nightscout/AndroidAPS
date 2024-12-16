package app.aaps.pump.common.hw.rileylink.data

import java.util.UUID

/**
 * Created by andy on 9/10/18.
 */
class BleAdvertisedData(val uuids: MutableList<UUID>, val name: String) {

    override fun toString(): String = "BleAdvertisedData [name=$name, UUIDs=$uuids]"
}
