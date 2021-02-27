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

    fun log(action: Action, s: String, vararg listvalues: ValueWithUnit) {
        val values = mutableListOf<ValueWithUnit>()
        for (v in listvalues){
            var vConverted = v
            // Convertion to always store all values in the same units in database
            when(v.unit) {
                Units.Mmol_L -> { vConverted = ValueWithUnit(v.dValue * Constants.MMOLL_TO_MGDL, Units.Mg_Dl)}
            }
            values.add(vConverted)
        }
        compositeDisposable += repository.runTransaction(UserEntryTransaction(
            action = action,
            s = s,
            values = values
        ))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribeBy(
                //onError = { aapsLogger.debug("ERRORED USER ENTRY: $action $s ${if (d1.dValue != 0.0) d1 else ""} ${if (d2.dValue != 0.0) d2 else ""} ${if (i1.iValue != 0) i1 else ""} ${if (i2.iValue != 0) i2 else ""}") },
                //onComplete = { aapsLogger.debug("USER ENTRY: $action $s ${if (d1.dValue != 0.0) d1 else ""} ${if (d2.dValue != 0.0) d2 else ""} ${if (i1.iValue != 0) i1 else ""} ${if (i2.iValue != 0) i2 else ""}") }
            )
    }

    fun log(action: Action, vararg listvalues: ValueWithUnit) {
        val values = mutableListOf<ValueWithUnit>()
        for (v in listvalues){
            var vConverted = v
            // Convertion to always store all values in the same units in database
            when(v.unit) {
                Units.Mmol_L -> { vConverted = ValueWithUnit(v.dValue * Constants.MMOLL_TO_MGDL, Units.Mg_Dl)}
            }
            values.add(vConverted)
        }
        compositeDisposable += repository.runTransaction(UserEntryTransaction(
            action = action,
            s = "",
            values = values
        ))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribeBy(
                //onError = { aapsLogger.debug("ERRORED USER ENTRY: $action $s ${if (d1.dValue != 0.0) d1 else ""} ${if (d2.dValue != 0.0) d2 else ""} ${if (i1.iValue != 0) i1 else ""} ${if (i2.iValue != 0) i2 else ""}") },
                //onComplete = { aapsLogger.debug("USER ENTRY: $action $s ${if (d1.dValue != 0.0) d1 else ""} ${if (d2.dValue != 0.0) d2 else ""} ${if (i1.iValue != 0) i1 else ""} ${if (i2.iValue != 0) i2 else ""}") }
            )
    }

    fun log(action: Action) {
        compositeDisposable += repository.runTransaction(UserEntryTransaction(
            action = action,
            s = ""
        ))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribeBy(
                onError = { aapsLogger.debug("ERRORED USER ENTRY: $action") },
                onComplete = { aapsLogger.debug("USER ENTRY: $action") }
            )
    }
}