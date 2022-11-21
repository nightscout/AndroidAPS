package info.nightscout.database.impl.daos.delegated

import info.nightscout.database.impl.daos.DeviceStatusDao
import info.nightscout.database.entities.interfaces.DBEntry

internal class DelegatedDeviceStatusDao(changes: MutableList<DBEntry>, private val dao: DeviceStatusDao) : DelegatedDao(changes), DeviceStatusDao by dao