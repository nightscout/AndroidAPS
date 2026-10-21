package app.aaps.implementation.resources

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStringIds
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.implementation.ImplementationStringIds
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import java.util.Locale

/**
 * Created by adrian on 2019-12-23.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ResourceHelperImpl(
    var context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val preferences: Preferences
) : ResourceHelper {

    // Lazy for the same reason as the context below: `Dispatchers.Main` does not exist in a plain JVM
    // test ("Dispatchers.Main was accessed when the platform dispatcher was absent"), and this class is
    // built for real there now that Metro owns it. Only `start()` uses the scope, so nothing is delayed
    // that anything waits on.
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    // Built on first use rather than at construction. `LocaleHelper.currentLocale` reads Android
    // resources, and this class is built for real in the plain-JVM graph tests once Metro owns it.
    @Volatile
    private var localizedContextOrNull: Context? = null

    // Guards building-and-storing against dropping, so a change of language cannot be overwritten by a
    // build that started before it. The preference is stored before the flow below emits, so a builder
    // either enters this lock before the drop - and is then dropped by it - or after, and reads the new
    // value. Without the lock a builder could store an old-language context after the drop and keep it
    // for the rest of the process. The fast path stays a plain volatile read, so `gs()` pays nothing.
    private val lock = Any()

    private val localizedContext: Context
        get() = localizedContextOrNull ?: synchronized(lock) {
            localizedContextOrNull ?: buildLocalizedContext().also { localizedContextOrNull = it }
        }

    /**
     * Registers the string owners this class is responsible for, and starts following the locale.
     *
     * **Called from the `Application`, not from `init`.** Doing it while the object is being built tied
     * it to whenever something happened to inject `ResourceHelper`, which is not a defined moment - the
     * same trap `BaseTestApp` records for the Firebase flags ("runs when that class is injected, far too
     * late here and often not at all"). It also made the class impossible for Metro to own, because a
     * contributed class is constructed for real in the plain-JVM graph tests.
     *
     * Must run before anything asks for text, so it belongs beside `registerStringOwners()` - the other
     * twelve owners are registered there already.
     */
    fun start() {
        // Teach ResourceHelper the names :core:ui owns. It cannot see CoreUiStringIds itself - :core:ui
        // depends on :core:interfaces, not the other way round - so without this a `ui` name read
        // outside a Composable renders as the raw name ("format_carbs" instead of "12 g").
        TextRefIdRegistry.register("coreUi") { name -> CoreUiStringIds.idOf(name) }
        // Same for this module's own names: the classes here name their strings now, and ResourceHelper
        // cannot see ImplementationStringIds from :core:interfaces either.
        TextRefIdRegistry.register("implementation") { name -> ImplementationStringIds.idOf(name) }

        // Both keys decide the language, because Simple mode forces it to "default". A recreate() of the
        // activity does not cover this class: it re-runs attachBaseContext/LocaleHelper.wrap for the
        // activity only, while the context cached here is built from the Application and would keep the
        // old language for the rest of the process.
        //
        // Only drop the cache; the getter builds it again on the next `gs()`, on the thread that asks,
        // after the new value is stored. That is why there is nothing to race on Main any more.
        //
        // Each flow is dropped on its own. `observe()` returns a StateFlow, so `merge(a, b).drop(1)`
        // would drop one initial value and let the other one through as if it were a change.
        merge(
            preferences.observe(StringKey.GeneralLanguage).drop(1),
            preferences.observe(BooleanKey.GeneralSimpleMode).drop(1)
        ).onEach {
            synchronized(lock) { localizedContextOrNull = null }
        }.launchIn(scope)
    }

    /**
     * A context whose resources are in the selected language.
     *
     * Never compare the selected locale with [Locale.getDefault] to decide this. `LocaleHelper.update()`
     * (from `MainApp.doInit`) and `LocaleHelper.wrap()` (from every `attachBaseContext`) both move the
     * default locale to the selected language, while the Application resources stay in the phone
     * language - `MainApp` does not wrap its own context. So after either of them has run, that
     * comparison matches and hands back phone-language text for an explicitly picked language.
     */
    private fun buildLocalizedContext(): Context =
        // The plain context is right for "default" only, and it is also the better answer there: it
        // follows a later change of the phone language, while a copied Configuration would not.
        if (LocaleHelper.isSystemDefault(context)) context
        else context.createConfigurationContext(
            Configuration(context.resources.configuration)
                .apply { setLocale(LocaleHelper.currentLocale(context)) }
        )

    override fun gs(@StringRes id: Int): String =
        localizedContext.resources.getString(id)

    override fun gs(@StringRes id: Int, vararg args: Any?): String {
        return try {
            localizedContext.resources.getString(id, *args)
        } catch (exception: Exception) {
            val resourceName = context.resources.getResourceEntryName(id)
            val resourceValue = context.getString(id)
            val currentLocale: Locale = context.resources.configuration.locales[0]
            fabricPrivacy.logMessage("Failed to get string for resource $resourceName ($id) '$resourceValue' for locale $currentLocale with args ${args.map { it.toString() }}")
            fabricPrivacy.logException(exception)
            try {
                gsNotLocalised(id, *args)
            } catch (exceptionNonLocalized: Exception) {
                fabricPrivacy.logMessage("Fallback failed to get string for resource $resourceName ($id) '$resourceValue' with args ${args.map { it.toString() }}")
                fabricPrivacy.logException(exceptionNonLocalized)
                "FAILED to get string $resourceName"
            }
        }
    }

    // Goes through localizedContext like gs(). It used the plain context from the start (2019), so
    // plurals - "x hours ago" in DateUtilAndroid, the objectives duration, the Omnipod alert count -
    // were in the phone language even after a restart.
    override fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
        localizedContext.resources.getQuantityString(id, quantity, *args)

    override fun gsNotLocalised(@StringRes id: Int, vararg args: Any?): String =
        with(Configuration(context.resources.configuration)) {
            setLocale(Locale.ENGLISH)
            context.createConfigurationContext(this).getString(id, *args)
        }

    // Reads the bool resource directly. It used to go through gb(), which was dropped from the
    // interface because this was its only caller.
    override fun shortTextMode(): Boolean = !context.resources.getBoolean(app.aaps.core.ui.R.bool.isTablet)
}
