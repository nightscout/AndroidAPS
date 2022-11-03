package info.nightscout.androidaps.plugins.pump.eopatch.vo

import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.core.Observable

interface IPreference<T: Any>{
    fun flush(sp: SP)
    fun observe(): Observable<T>
}