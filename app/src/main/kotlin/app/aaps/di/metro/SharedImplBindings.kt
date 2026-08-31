package app.aaps.di.metro

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.impl.logging.AAPSLoggerProduction
import app.aaps.shared.impl.logging.LImpl
import app.aaps.shared.impl.rx.AapsSchedulersImpl
import app.aaps.shared.impl.rx.bus.RxBusImpl
import app.aaps.shared.impl.sharedPreferences.SPImpl
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

    /** Takes the [SharedPreferences] from `AppAndroidBindings` rather than opening the file twice. */
    @Provides
    @SingleIn(AppScope::class)
    fun sp(sharedPreferences: SharedPreferences, context: Context): SP = SPImpl(sharedPreferences, context)

    /**
     * The same object, seen as the platform neutral half of itself.
     * Common code such as `PreferencesImpl` asks for [KeyValueStore] rather than [SP], because a
     * resource id overload means nothing off Android. `SP` extends it, so this hands over the one
     * instance rather than building a second store.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun keyValueStore(sp: SP): KeyValueStore = sp

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
