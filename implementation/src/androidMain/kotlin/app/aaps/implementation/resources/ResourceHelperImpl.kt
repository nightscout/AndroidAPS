package app.aaps.implementation.resources

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.UiStringIds
import app.aaps.implementation.ImplementationStringIds
import app.aaps.core.ui.locale.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by adrian on 2019-12-23.
 */
@Singleton
class ResourceHelperImpl @Inject constructor(var context: Context, private val fabricPrivacy: FabricPrivacy, preferences: Preferences) : ResourceHelper {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var localizedContext: Context = buildLocalizedContext()

    init {
        // Teach ResourceHelper the names :core:ui owns. It cannot see UiStringIds itself - :core:ui
        // depends on :core:interfaces, not the other way round - so without this a `ui` name read
        // outside a Composable renders as the raw name ("format_carbs" instead of "12 g").
        // Here rather than in :core:ui, because this class is downstream of every module that owns
        // strings and is built before anything can ask it for text.
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        // Same for this module's own names: the classes here name their strings now, and ResourceHelper
        // cannot see ImplementationStringIds from :core:interfaces either.
        TextRefIdRegistry.register("implementation") { name -> ImplementationStringIds.idOf(name) }

        // GeneralLanguage changes trigger Activity.recreate() which rebuilds the context
        // via attachBaseContext/LocaleHelper.wrap — no need to rebuild here and race on Main.
        preferences.observe(BooleanKey.GeneralSimpleMode).drop(1).onEach {
            localizedContext = buildLocalizedContext()
        }.launchIn(scope)
    }

    private fun buildLocalizedContext(): Context {
        val locale = LocaleHelper.currentLocale(context)
        return if (locale == Locale.getDefault()) context
        else context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) }
        )
    }

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

    override fun gq(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
        context.resources.getQuantityString(id, quantity, *args)

    override fun gsNotLocalised(@StringRes id: Int, vararg args: Any?): String =
        with(Configuration(context.resources.configuration)) {
            setLocale(Locale.ENGLISH)
            context.createConfigurationContext(this).getString(id, *args)
        }

    // Reads the bool resource directly. It used to go through gb(), which was dropped from the
    // interface because this was its only caller.
    override fun shortTextMode(): Boolean = !context.resources.getBoolean(app.aaps.core.ui.R.bool.isTablet)
}
