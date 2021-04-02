package info.nightscout.androidaps.logging

import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.XXXValueWithUnit
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.UserEntry.ValueWithUnit
import info.nightscout.androidaps.database.transactions.UserEntryTransaction
import info.nightscout.androidaps.utils.rx.AapsSchedulers
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

    @Deprecated("Use XXXValueWithUnits")
    fun log(action: Action, s: String? ="", vararg listvalues: ValueWithUnit) {
    }

    @Deprecated("Use XXXValueWithUnits")
    fun log(action: Action, vararg listValues: ValueWithUnit) {
    }

    @Deprecated("Use XXXValueWithUnits")
    fun log(action: Action, s: String? = "") {}

    fun log(action: Action, source: Sources, note: String? ="", vararg listvalues: XXXValueWithUnit?) = log(action, source, note, listvalues.toList())

    fun log(action: Action, source: Sources, vararg listvalues: XXXValueWithUnit?) = log(action, source,"", listvalues.toList())

    fun log(action: Action, source: Sources, note: String? ="", listvalues: List<XXXValueWithUnit?> = listOf()) {
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
}