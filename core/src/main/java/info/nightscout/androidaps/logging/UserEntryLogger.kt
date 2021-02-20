package info.nightscout.androidaps.logging

import info.nightscout.androidaps.database.AppRepository
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

    fun log(action: String, s: String = "", d1: Double = 0.0, d2: Double = 0.0, i1: Int = 0, i2: Int = 0) {
        compositeDisposable += repository.runTransaction(UserEntryTransaction(
            action = action,
            s = s,
            d1 = d1,
            d2 = d2,
            i1 = i1,
            i2 = i2
        ))
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.io)
            .subscribeBy(
                onError = { aapsLogger.debug("ERRORED USER ENTRY: $action $s ${if (d1 == 0.0) d1 else ""} ${if (d2 == 0.0) d2 else ""} ${if (i1 == 0) i1 else ""} ${if (i2 == 0) i2 else ""}") },
                onComplete = { aapsLogger.debug("USER ENTRY: $action $s ${if (d1 == 0.0) d1 else ""} ${if (d2 == 0.0) d2 else ""} ${if (i1 == 0) i1 else ""} ${if (i2 == 0) i2 else ""}") }
            )
    }
}