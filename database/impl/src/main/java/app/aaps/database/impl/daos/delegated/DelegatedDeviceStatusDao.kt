package app.aaps.database.impl.daos.delegated

import app.aaps.database.entities.interfaces.DBEntry
import app.aaps.database.impl.daos.DeviceStatusDao

internal class DelegatedDeviceStatusDao(changes: MutableList<DBEntry>, private val dao: DeviceStatusDao) : DelegatedDao(changes), DeviceStatusDao by dao