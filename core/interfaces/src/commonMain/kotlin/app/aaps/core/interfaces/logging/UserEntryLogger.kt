package app.aaps.core.interfaces.logging

import app.aaps.core.data.model.UE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit

interface UserEntryLogger {

    fun log(action: Action, source: Sources, note: String? = "", timestamp: Long, listValues: List<ValueWithUnit>)
    fun log(action: Action, source: Sources, note: String? = "", value: ValueWithUnit)
    fun log(action: Action, source: Sources, note: String? = "", listValues: List<ValueWithUnit> = listOf())
    fun log(entries: List<UE>)
}