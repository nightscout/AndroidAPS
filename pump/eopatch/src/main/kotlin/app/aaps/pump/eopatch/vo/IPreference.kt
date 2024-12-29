package app.aaps.pump.eopatch.vo

import app.aaps.core.interfaces.sharedPreferences.SP
import io.reactivex.rxjava3.core.Observable

interface IPreference<T : Any> {

    fun flush(sp: SP)
    fun observe(): Observable<T>
}