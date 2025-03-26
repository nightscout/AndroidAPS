package app.aaps.pump.eopatch.vo

import app.aaps.core.keys.interfaces.Preferences
import io.reactivex.rxjava3.core.Observable

interface IPreference<T : Any> {

    fun flush(preferences: Preferences)
    fun observe(): Observable<T>
}