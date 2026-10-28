package app.aaps.implementation.resources

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.CoreUiStringIds
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.implementation.ImplementationStringIds
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.Locale

/**
 * Created by adrian on 2019-12-23.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ResourceHelperImpl(
    var context: Context,
    private val fabricPrivacy: FabricPrivacy
) : ResourceHelper {

    /** A context in the selected language, and the language it was built for. */
    private class Localized(val language: String, val context: Context)

    // Built on first use rather than at construction. `LocaleHelper.currentLocale` reads Android
    // resources, and this class is built for real in the plain-JVM graph tests once Metro owns it.
    @Volatile
    private var localized: Localized? = null

    // Checked against the stored language on every use, not dropped by a listener when the language
    // changes. A listener ran on its own, so code reacting to the same change - the overview reading its
    // texts again after a language switch - could get here first and build its text from the old
    // context. Reading the language is a lookup in the settings, which are held in memory.
    //
    // Both settings decide the language, because Simple mode forces it to "default". A recreate() of the
    // activity does not cover this class: it re-runs attachBaseContext/LocaleHelper.wrap for the
    // activity only, while the context here is built from the Application.
    //
    // Two threads may build at the same time and the older one may be stored last. That heals itself:
    // its language no longer matches, so the next use builds again.
    private val localizedContext: Context
        get() {
            val language = LocaleHelper.selectedLanguage()
            localized?.let { if (it.language == language) return it.context }
            return buildLocalizedContext().also { localized = Localized(language, it) }
        }

    /**
     * Registers the string owners this class is responsible for.
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
        if (LocaleHelper.isSystemDefault()) context
        else context.createConfigurationContext(
            Configuration(context.resources.configuration)
                .apply { setLocale(LocaleHelper.currentLocale()) }
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
}
