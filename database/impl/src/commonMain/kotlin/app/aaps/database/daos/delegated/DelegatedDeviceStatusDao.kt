package app.aaps.database.daos.delegated

import app.aaps.database.daos.DeviceStatusDao
import app.aaps.database.entities.interfaces.DBEntry

internal class DelegatedDeviceStatusDao(changes: MutableList<DBEntry>, private val dao: DeviceStatusDao) : DelegatedDao(changes), DeviceStatusDao by dao