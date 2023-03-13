package info.nightscout.interfaces.logging

import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.interfaces.userEntry.UserEntryMapper
import info.nightscout.interfaces.userEntry.ValueWithUnitMapper

interface UserEntryLogger {

    fun log(action: Action, source: Sources, note: String?, timestamp: Long, vararg listValues: ValueWithUnit?)
    fun log(action: Action, source: Sources, note: String?, timestamp: Long, listValues: List<ValueWithUnit?>)
    fun log(action: Action, source: Sources, note: String? = "", vararg listValues: ValueWithUnit?)
    fun log(action: Action, source: Sources, vararg listValues: ValueWithUnit?)
    fun log(action: Action, source: Sources, note: String? = "", listValues: List<ValueWithUnit?> = listOf())
    fun log(entries: List<UserEntry>)
    fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, note: String? = "", vararg listValues: ValueWithUnitMapper?)
    fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, vararg listValues: ValueWithUnitMapper?)
    fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, note: String? = "", listValues: List<ValueWithUnitMapper?> = listOf())
}