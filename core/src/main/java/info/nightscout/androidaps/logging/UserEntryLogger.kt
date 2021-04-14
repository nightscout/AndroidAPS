package info.nightscout.androidaps.logging

import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.transactions.UserEntryTransaction
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.userEntry.UserEntryMapper
import info.nightscout.androidaps.utils.userEntry.ValueWithUnitMapper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserEntryLogger @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val aapsSchedulers: AapsSchedulers
) {

    private val compositeDisposable = CompositeDisposable()

    fun log(action: Action, source: Sources, note: String? ="", vararg listvalues: ValueWithUnit?) = log(action, source, note, listvalues.toList())

    fun log(action: Action, source: Sources, vararg listvalues: ValueWithUnit?) = log(action, source,"", listvalues.toList())

    fun log(action: Action, source: Sources, note: String? ="", listvalues: List<ValueWithUnit?> = listOf()) {
        val filteredValues = listvalues.toList().filter { it != null}
        compositeDisposable += repository.runTransaction(UserEntryTransaction(
            action = action,
            source = source,
            note = note ?: "",
            values = filteredValues
        ))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribeBy(
                onError = { aapsLogger.debug("ERRORED USER ENTRY: $action $source $note $filteredValues") },
                onComplete = { aapsLogger.debug("USER ENTRY: $action $source $note $filteredValues") }
            )
    }

    fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, note: String? ="", vararg listvalues: ValueWithUnitMapper?) = log(action.db, source.db, note, listvalues.toList().map {it?.db()})

    fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, vararg listvalues: ValueWithUnitMapper?) = log(action.db, source.db, "", listvalues.toList().map {it?.db()})

    fun log(action: UserEntryMapper.Action, source: UserEntryMapper.Sources, note: String? ="", listvalues: List<ValueWithUnitMapper?> = listOf()) = log(action.db, source.db, note, listvalues.map {it?.db()})

}