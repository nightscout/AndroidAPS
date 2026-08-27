package app.aaps.implementation.logging

import app.aaps.core.data.model.UE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.utils.DateUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class UserEntryLoggerImpl @Inject constructor(
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val appScope: CoroutineScope
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
