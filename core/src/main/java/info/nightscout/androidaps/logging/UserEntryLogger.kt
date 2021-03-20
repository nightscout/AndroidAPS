package info.nightscout.androidaps.logging

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry.*
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

    fun log(action: Action, s: String? ="", vararg listvalues: ValueWithUnit) {
        val values = mutableListOf<ValueWithUnit>()
        for (v in listvalues){
            if (v.condition) values.add(v)
        }
        compositeDisposable += repository.runTransaction(UserEntryTransaction(
            action = action,
            s = s ?:"",
            values = values
        ))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribeBy(
                onError = { aapsLogger.debug("ERRORED USER ENTRY: $action $s $values") },
                onComplete = { aapsLogger.debug("USER ENTRY: $action $s $values") }
            )
    }

    fun log(action: Action, vararg listvalues: ValueWithUnit) {
        val values = mutableListOf<ValueWithUnit>()
        for (v in listvalues){
            if (v.condition) values.add(v)
        }
        compositeDisposable += repository.runTransaction(UserEntryTransaction(
            action = action,
            s = "",
            values = values
        ))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribeBy(
                onError = { aapsLogger.debug("ERRORED USER ENTRY: $action $values") },
                onComplete = { aapsLogger.debug("USER ENTRY: $action $values") }
            )
    }

    fun log(action: Action, s: String? = "") {
        compositeDisposable += repository.runTransaction(UserEntryTransaction(
            action = action,
            s = s ?:""
        ))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribeBy(
                onError = { aapsLogger.debug("ERRORED USER ENTRY: $action") },
                onComplete = { aapsLogger.debug("USER ENTRY: $action") }
            )
    }

    fun log(action: Action, s: String? = "",  values: MutableList<ValueWithUnit>) {
        compositeDisposable += repository.runTransaction(UserEntryTransaction(
            action = action,
            s = s ?:"",
            values = values
        ))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribeBy(
                onError = { aapsLogger.debug("ERRORED USER ENTRY: $action $s $values") },
                onComplete = { aapsLogger.debug("USER ENTRY: $action $s $values") }
            )
    }
}