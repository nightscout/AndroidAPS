package info.nightscout.androidaps.utils.alertDialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import info.nightscout.androidaps.core.R

object AlertDialogHelper {

    @Suppress("FunctionName")
    fun Builder(context: Context, @StyleRes themeResId: Int = R.style.AppTheme) =
        AlertDialog.Builder(ContextThemeWrapper(context, themeResId))

    fun buildCustomTitle(context: Context, title: String,
                         @DrawableRes iconResource: Int = R.drawable.ic_check_while_48dp,
                         @StyleRes themeResId: Int = R.style.AppTheme,
                         @LayoutRes layoutResource: Int = R.layout.dialog_alert_custom): View? {
        val titleLayout = LayoutInflater.from(ContextThemeWrapper(context, themeResId)).inflate(layoutResource, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).text = title
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(iconResource)
        titleLayout.findViewById<View>(R.id.alertdialog_title).isSelected = true
        return titleLayout
    }

}