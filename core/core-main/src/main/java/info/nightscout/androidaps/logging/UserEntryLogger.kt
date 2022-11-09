package info.nightscout.androidaps.logging

import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.UserEntryTransaction
import info.nightscout.shared.utils.DateUtil
import info.nightscout.androidaps.utils.userEntry.UserEntryMapper
import info.nightscout.androidaps.utils.userEntry.ValueWithUnitMapper
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.logging.AAPSLogger
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class UserEntryLogger @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val aapsSchedulers: AapsSchedulers,
    private val dateUtil: DateUtil
) {

    private val compositeDisposable = CompositeDisposable()

    fun log(action: Action, source: Sources, note: String? = "", vararg listValues: ValueWithUnit?) = log(action, source, note, listValues.toList())

    fun log(action: Action, source: Sources, vararg listValues: ValueWithUnit?) = log(action, source, "", listValues.toList())

    fun log(action: Action, source: Sources, note: String? = "", listValues: List<ValueWithUnit?> = listOf()) {
        val filteredValues = listValues.toList().filterNotNull()
        log(listOf(UserEntryTransaction.Entry(dateUtil.now(), action, source, note ?: "", filteredValues)))
    }

    fun log(entries: List<UserEntryTransaction.Entry>) {
        compositeDisposable += repository.runTransactionForResult(UserEntryTransaction(entries))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribe(
                { result -> result.forEach { aapsLogger.debug("USER ENTRY: ${dateUtil.dateAndTimeAndSecondsString(it.timestamp)} ${it.action} ${it.source} ${it.note} ${it.values}") } },
                { aapsLogger.debug("FAILED USER ENTRY: $it $entries") },
            )
    }

    fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, note: String? = "", vararg listValues: ValueWithUnitMapper?) =
        log(action.db, source.db, note, listValues.toList().map { it?.db() })

    fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, vararg listValues: ValueWithUnitMapper?) = log(action.db, source.db, "", listValues.toList().map { it?.db() })

    fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, note: String? = "", listValues: List<ValueWithUnitMapper?> = listOf()) =
        log(action.db, source.db, note, listValues.map { it?.db() })

}