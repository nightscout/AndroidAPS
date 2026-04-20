package info.nightscout.androidaps.plugins.pump.carelevo.data.common

import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.WriteToCharacteristic
import java.util.UUID

class CarelevoWriteCommandBuilder {

    private var address : String? = null
    private var rxUuid : UUID? = null
    private var writeType : Int? = null
    private var payload : ByteArray? = null

    fun address(address : String) : CarelevoWriteCommandBuilder {
        this.address = address
        return this
    }

    fun rxUuid(uuid : UUID) : CarelevoWriteCommandBuilder {
        this.rxUuid = uuid
        return this
    }

    fun writeType(type : Int) : CarelevoWriteCommandBuilder {
        this.writeType = type
        return this
    }

    fun payload(payload : ByteArray) : CarelevoWriteCommandBuilder {
        this.payload = payload
        return this
    }

    fun build() : WriteToCharacteristic {
        return WriteToCharacteristic(
            requireNotNull(address) { "address must be not null" },
            requireNotNull(rxUuid) { "RxUUID must be not null" },
            requireNotNull(writeType) { "Write type must be not null" },
            requireNotNull(payload) { "Payload must be not null" }
        )
    }
}