package app.aaps.di.metro

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.impl.logging.AAPSLoggerProduction
import app.aaps.shared.impl.logging.LImpl
import app.aaps.shared.impl.rx.AapsSchedulersImpl
import app.aaps.settingsstore.AndroidStorage
import app.aaps.shared.impl.rx.bus.RxBusImpl
import app.aaps.shared.impl.utils.DateUtilImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * The phone's half of `:shared:impl`.
 * Keep this in step with `SharedImplModule` and `LoggerModule`. A binding that only exists on one side
 * is harmless - the other side fails to compile the moment it needs it. The danger is a binding that
 * exists on both sides but is *built differently*: change the preferences file name in one and the
 * phone and the watch quietly disagree, with nothing failing.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object SharedImplBindings {

    /**
     * The preference store: the encrypted one [AndroidStorage] opened when the app started, or the empty one
     * in memory when that failed. Wear keeps its own store, `SPImpl` (see `SharedImplModule`).
     */
    @Provides
    @SingleIn(AppScope::class)
    fun keyValueStore(context: Context): KeyValueStore = AndroidStorage.open(context).store

    /** Deferred: [L] reads its log settings back out of [Preferences]. */
    @Provides
    @SingleIn(AppScope::class)
    fun l(preferences: () -> Preferences): L = LImpl(preferences)

    @Provides
    @SingleIn(AppScope::class)
    fun aapsLogger(l: L): AAPSLogger = AAPSLoggerProduction(l)

    @Provides
    @SingleIn(AppScope::class)
    fun dateUtil(context: Context): DateUtil = DateUtilImpl(context)

    @Provides
    @SingleIn(AppScope::class)
    fun rxBus(aapsLogger: AAPSLogger): RxBus = RxBusImpl(aapsLogger)

    @Provides
    @SingleIn(AppScope::class)
    fun aapsSchedulers(): AapsSchedulers = AapsSchedulersImpl()
}
