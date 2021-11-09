package info.nightscout.androidaps.database.transactions

import info.nightscout.androidaps.database.entities.DeviceStatus

class UpdateNsIdDeviceStatusTransaction(val deviceStatus: DeviceStatus) : Transaction<Unit>() {

    override fun run() {
        val current = database.deviceStatusDao.findById(deviceStatus.id)
        if (current != null && current.interfaceIDs.nightscoutId != deviceStatus.interfaceIDs.nightscoutId) {
            current.interfaceIDs.nightscoutId = deviceStatus.interfaceIDs.nightscoutId
            database.deviceStatusDao.update(current)
        }
    }
}