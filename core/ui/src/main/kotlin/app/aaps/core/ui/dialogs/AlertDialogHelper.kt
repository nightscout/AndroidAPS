package app.aaps.core.ui.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.aaps.core.ui.R

object AlertDialogHelper {

    @Suppress("FunctionName")
    fun Builder(context: Context, @StyleRes themeResId: Int = R.style.AppTheme) =
        MaterialAlertDialogBuilder(ContextThemeWrapper(context, themeResId))

    fun buildCustomTitle(context: Context, title: String,
                         @DrawableRes iconResource: Int = R.drawable.ic_check_white_48dp,
                         @StyleRes themeResId: Int = R.style.AppTheme,
                         @LayoutRes layoutResource: Int = R.layout.dialog_alert_custom_title): View? {
        val titleLayout = LayoutInflater.from(ContextThemeWrapper(context, themeResId)).inflate(layoutResource, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).apply {
            text = title
            isSelected = true
        }
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(iconResource)
        return titleLayout
    }

}