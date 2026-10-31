package app.aaps.implementation.resources

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.CoreUiStringIds
import app.aaps.implementation.ImplementationStringIds
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.Locale

/**
 * Text from the app's resources.
 *
 * They are in the language picked in AAPS: on Android 13 and newer `MainApp` gives it to Android as this app's
 * language, and Android applies it to the whole app, also to the application context used here. On Android 12
 * there are no app languages, and AAPS follows the phone's language.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ResourceHelperImpl(
    var context: Context,
    private val fabricPrivacy: FabricPrivacy
) : ResourceHelper {

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

    override fun gs(@StringRes id: Int): String =
        context.resources.getString(id)

    override fun gs(@StringRes id: Int, vararg args: Any?): String {
        return try {
            context.resources.getString(id, *args)
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
}
