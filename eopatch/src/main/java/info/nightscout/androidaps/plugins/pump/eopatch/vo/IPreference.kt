package info.nightscout.androidaps.plugins.pump.eopatch.vo

import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.Observable

interface IPreference<T>{
    open fun flush(sp: SP)
    open fun observe(): Observable<T>
}