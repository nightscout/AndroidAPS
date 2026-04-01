package app.aaps.implementation.logging

import app.aaps.core.data.model.UE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.utils.DateUtil
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@Reusable
class UserEntryLoggerImpl @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    @ApplicationScope private val appScope: CoroutineScope
) : UserEntryLogger {

    override fun log(action: Action, source: Sources, note: String?, timestamp: Long, listValues: List<ValueWithUnit>) {
        log(listOf(UE(timestamp = timestamp, action = action, source = source, note = note ?: "", values = listValues.toList())))
    }

    override fun log(action: Action, source: Sources, note: String?, value: ValueWithUnit) =
        log(action, source, note, listOf(value))

    override fun log(action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit>) =
        log(action, source, note, dateUtil.now(), listValues)

    override fun log(entries: List<UE>) {
        appScope.launch { persistenceLayer.insertUserEntries(entries) }
    }
}
