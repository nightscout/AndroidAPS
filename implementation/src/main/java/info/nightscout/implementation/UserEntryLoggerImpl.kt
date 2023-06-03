package info.nightscout.implementation

import dagger.Reusable
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.UserEntryTransaction
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.userEntry.UserEntryMapper
import info.nightscout.interfaces.userEntry.ValueWithUnitMapper
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

@OpenForTesting
@Reusable
class UserEntryLoggerImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val aapsSchedulers: AapsSchedulers,
    private val dateUtil: DateUtil
) : UserEntryLogger {

    private val compositeDisposable = CompositeDisposable()

    override fun log(action: Action, source: Sources, note: String?, timestamp: Long, vararg listValues: ValueWithUnit?) = log(action, source, note, timestamp, listValues.toList())

    override fun log(action: Action, source: Sources, note: String?, timestamp: Long, listValues: List<ValueWithUnit?>) {
        val filteredValues = listValues.toList().filterNotNull()
        log(
            listOf(
                UserEntry(
                    timestamp = timestamp,
                    action = action,
                    source = source,
                    note = note ?: "",
                    values = filteredValues
                )
            )
        )
    }

    override fun log(action: Action, source: Sources, note: String?, vararg listValues: ValueWithUnit?) = log(action, source, note, listValues.toList())

    override fun log(action: Action, source: Sources, vararg listValues: ValueWithUnit?) = log(action, source, "", listValues.toList())

    override fun log(action: Action, source: Sources, note: String?, listValues: List<ValueWithUnit?>) = log(action, source, note, dateUtil.now(), listValues)

    override fun log(entries: List<UserEntry>) {
        compositeDisposable += repository.runTransactionForResult(UserEntryTransaction(entries))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                { result -> result.forEach { aapsLogger.debug("USER ENTRY: ${dateUtil.dateAndTimeAndSecondsString(it.timestamp)} ${it.action} ${it.source} ${it.note} ${it.values}") } },
                { aapsLogger.debug("FAILED USER ENTRY: $it $entries") },
            )
    }

    override fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, note: String?, vararg listValues: ValueWithUnitMapper?) =
        log(action.db, source.db, note, listValues.toList().map { it?.db() })

    override fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, vararg listValues: ValueWithUnitMapper?) = log(action.db, source.db, "", listValues.toList().map { it?.db() })

    override fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, note: String?, listValues: List<ValueWithUnitMapper?>) =
        log(action.db, source.db, note, listValues.map { it?.db() })

}