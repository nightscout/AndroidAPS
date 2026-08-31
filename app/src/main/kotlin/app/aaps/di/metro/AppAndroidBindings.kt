package app.aaps.di.metro

import android.content.Context
import android.content.SharedPreferences
import android.telephony.SmsManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.utils.receivers.DataInbox
import app.aaps.history.HistoryBrowserData
import app.aaps.ui.compose.history.HistoryScope
import app.aaps.shared.impl.sharedPreferences.defaultPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Android framework objects the graph builds itself, rather than borrowing from Dagger.
 *
 * These have no interface and no implementation class to annotate, so they cannot be contributed with
 * `@ContributesBinding` - they need a container with a `@Provides`. `:app` is Android only, so this can
 * live here; a module that is multiplatform would need the same thing in its **androidMain**, the way
 * `CoreObjectsAndroidContainer` does for `CryptoUtil`.
 *
 * Dagger consumers still get these through the delegates in `CoreObjectsModule`, so there is exactly one
 * instance either way.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object AppAndroidBindings {

    /** Same file and mode as the Dagger provider this replaces - it must be the same preferences file. */
    @Provides
    @SingleIn(AppScope::class)
    fun sharedPreferences(context: Context): SharedPreferences =
        defaultPreferences(context)

    /**
     * Metro would construct this by itself - it has an `@Inject` constructor - but only the scope makes
     * it a singleton, and it must be one: it is the inbox the broadcast receivers hand data to. It is
     * declared here rather than annotated in place because `:core:utils` does not run Metro.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun dataInbox(context: Context): DataInbox = DataInbox(context)

    /**
     * The SMS service, which is null on a device without telephony.
     *
     * Metro needs its own copy now that it builds `SmsCommunicatorPlugin` - the Dagger provider in
     * `CoreObjectsModule` only ever served the Dagger graph.
     */
    @Suppress("DEPRECATION")
    @Provides
    fun smsManager(context: Context): SmsManager? = context.getSystemService(SmsManager::class.java)

    /**
     * `ResourceHelper` is the Android implementation of the multiplatform [TextResolver].
     *
     * Was an `AapsLeaves` entry, which it never needed to be - it takes a Metro-provided parameter and
     * returns it, so nothing about it was Dagger's.
     */
    @Provides
    fun textResolver(rh: ResourceHelper): TextResolver = rh

    /**
     * The History Browser's own calculation objects.
     *
     * Scoped here rather than on the class: the window must be created **once**, because the whole
     * point of `HistoryBrowserData` is that browsing history does not share - and so cannot rewrite -
     * the state the running loop calculates on. An unscoped provider would hand out a fresh window per
     * injection point and quietly undo that.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun historyScope(historyWindowFactory: HistoryWindowGraph.Factory): HistoryScope =
        HistoryBrowserData(historyWindowFactory.create())
}
