package info.nightscout.androidaps.database.daos.delegated

import info.nightscout.androidaps.database.daos.DeviceStatusDao
import info.nightscout.androidaps.database.interfaces.DBEntry

internal class DelegatedDeviceStatusDao(changes: MutableList<DBEntry>, private val dao: DeviceStatusDao) : DelegatedDao(changes), DeviceStatusDao by dao