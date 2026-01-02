package app.aaps.implementation.resources

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.ArrayRes
import androidx.annotation.BoolRes
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.getThemeColor
import app.aaps.core.ui.locale.LocaleHelper
import dagger.Reusable
import java.util.Locale
import javax.inject.Inject

/**
 * Created by adrian on 2019-12-23.
 */
@Reusable
class ResourceHelperImpl @Inject constructor(var context: Context, private val fabricPrivacy: FabricPrivacy) : ResourceHelper {

    override fun gs(@StringRes id: Int): String =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(LocaleHelper.currentLocale(context)) }).resources.getString(id)

    override fun gs(@StringRes id: Int, vararg args: Any?): String {
        return try {
            context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(LocaleHelper.currentLocale(context)) }).resources.getString(id, *args)
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

    override fun gc(@ColorRes id: Int): Int = ContextCompat.getColor(context, id)

    override fun gd(@DrawableRes id: Int): Drawable? = AppCompatResources.getDrawable(context, id)

    override fun gb(@BoolRes id: Int): Boolean = context.resources.getBoolean(id)

    @SuppressLint("ResourceType")
    override fun gcs(@ColorRes id: Int): String =
        gs(id).replace("#ff", "#")

    override fun gsa(@ArrayRes id: Int): Array<String> =
        context.resources.getStringArray(id)

    override fun openRawResourceFd(id: Int): AssetFileDescriptor =
        context.resources.openRawResourceFd(id)

    override fun decodeResource(id: Int): Bitmap =
        BitmapFactory.decodeResource(context.resources, id)

    override fun getDisplayMetrics(): DisplayMetrics =
        context.resources.displayMetrics

    override fun dpToPx(dp: Int): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    override fun dpToPx(dp: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    override fun shortTextMode(): Boolean = !gb(app.aaps.core.ui.R.bool.isTablet)

    override fun gac(context: Context?, attributeId: Int): Int =
        (ContextThemeWrapper(context ?: this.context, app.aaps.core.ui.R.style.AppTheme)).getThemeColor(attributeId)

    override fun gac(attributeId: Int): Int =
        ContextThemeWrapper(this.context, app.aaps.core.ui.R.style.AppTheme).getThemeColor(attributeId)

    override fun getThemedCtx(context: Context): Context {
        val res: Resources = context.resources
        val configuration = Configuration(res.configuration)
        val filter = res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()

        configuration.uiMode = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO  -> Configuration.UI_MODE_NIGHT_NO or filter
            AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES or filter
            else                             -> res.configuration.uiMode
        }
        return context.createConfigurationContext(configuration)
    }
}
