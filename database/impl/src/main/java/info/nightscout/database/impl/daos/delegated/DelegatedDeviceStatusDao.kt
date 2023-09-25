package info.nightscout.database.impl.daos.delegated

import app.aaps.database.entities.interfaces.DBEntry
import info.nightscout.database.impl.daos.DeviceStatusDao

internal class DelegatedDeviceStatusDao(changes: MutableList<DBEntry>, private val dao: DeviceStatusDao) : DelegatedDao(changes), DeviceStatusDao by dao