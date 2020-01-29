package info.nightscout.androidaps.utils

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.os.SystemClock
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R

object OKDialog {
    @JvmStatic
    @JvmOverloads
    fun show(context: Context, title: String, message: String, runnable: Runnable? = null) {
        var notEmptytitle = title
        if (notEmptytitle.isEmpty()) notEmptytitle = MainApp.gs(R.string.message)
        val titleLayout = LayoutInflater.from(context).inflate(R.layout.dialog_alert_custom, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).text = notEmptytitle
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(R.drawable.ic_check_while_48dp)
        AlertDialog.Builder(ContextThemeWrapper(context, R.style.AppTheme))
            .setCustomTitle(titleLayout)
            .setMessage(message)
            .setPositiveButton(MainApp.gs(R.string.ok)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                runOnUiThread(runnable)
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }

    fun runOnUiThread(theRunnable: Runnable?) {
        val mainHandler = Handler(MainApp.instance().applicationContext.mainLooper)
        theRunnable?.let { mainHandler.post(it) }
    }

    @JvmStatic
    @JvmOverloads
    fun show(activity: Activity, title: String, message: Spanned, runnable: Runnable? = null) {
        var notEmptytitle = title
        if (notEmptytitle.isEmpty()) notEmptytitle = MainApp.gs(R.string.message)
        val titleLayout = activity.layoutInflater.inflate(R.layout.dialog_alert_custom, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).text = notEmptytitle
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(R.drawable.ic_check_while_48dp)
        AlertDialog.Builder(ContextThemeWrapper(activity, R.style.AppTheme))
            .setCustomTitle(titleLayout)
            .setMessage(message)
            .setPositiveButton(MainApp.gs(R.string.ok)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                runnable?.let { activity.runOnUiThread(it) }
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirmation(activity: Activity, message: String, ok: Runnable?) {
        showConfirmation(activity, MainApp.gs(R.string.confirmation), message, ok, null)
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirmation(activity: Activity, message: Spanned, ok: Runnable?) {
        showConfirmation(activity, MainApp.gs(R.string.confirmation), message, ok, null)
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirmation(activity: Activity, title: String, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        val titleLayout = activity.layoutInflater.inflate(R.layout.dialog_alert_custom, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).text = title
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(R.drawable.ic_check_while_48dp)
        AlertDialog.Builder(ContextThemeWrapper(activity, R.style.AppTheme))
            .setMessage(message)
            .setCustomTitle(titleLayout)
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                ok?.let { activity.runOnUiThread(it) }
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                cancel?.let { activity.runOnUiThread(it) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @JvmStatic
    fun showConfirmation(activity: Activity, title: String, message: String, ok: Runnable?, cancel: Runnable? = null) {
        val titleLayout = activity.layoutInflater.inflate(R.layout.dialog_alert_custom, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).text = title
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(R.drawable.ic_check_while_48dp)
        AlertDialog.Builder(ContextThemeWrapper(activity, R.style.AppTheme))
            .setMessage(message)
            .setCustomTitle(titleLayout)
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                ok?.let { activity.runOnUiThread(it) }
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                cancel?.let { activity.runOnUiThread(it) }
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        showConfirmation(context, MainApp.gs(R.string.confirmation), message, ok, cancel)
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, title: String, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        val titleLayout = LayoutInflater.from(context).inflate(R.layout.dialog_alert_custom, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).text = title
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(R.drawable.ic_check_while_48dp)
        AlertDialog.Builder(ContextThemeWrapper(context, R.style.AppTheme))
            .setMessage(message)
            .setCustomTitle(titleLayout)
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                runOnUiThread(ok)
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                runOnUiThread(cancel)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, message: String, ok: Runnable?, cancel: Runnable? = null) {
        showConfirmation(context, MainApp.gs(R.string.confirmation), message, ok, cancel)
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, title: String, message: String, ok: Runnable?, cancel: Runnable? = null) {
        val titleLayout = LayoutInflater.from(context).inflate(R.layout.dialog_alert_custom, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).text = title
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(R.drawable.ic_check_while_48dp)
        AlertDialog.Builder(ContextThemeWrapper(context, R.style.AppTheme))
            .setMessage(message)
            .setCustomTitle(titleLayout)
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                runOnUiThread(ok)
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                runOnUiThread(cancel)
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, title: String, message: String, ok: DialogInterface.OnClickListener?, cancel: DialogInterface.OnClickListener? = null) {
        val titleLayout = LayoutInflater.from(context).inflate(R.layout.dialog_alert_custom, null)
        (titleLayout.findViewById<View>(R.id.alertdialog_title) as TextView).text = title
        (titleLayout.findViewById<View>(R.id.alertdialog_icon) as ImageView).setImageResource(R.drawable.ic_check_while_48dp)
        AlertDialog.Builder(ContextThemeWrapper(context, R.style.AppTheme))
            .setMessage(message)
            .setCustomTitle(titleLayout)
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                ok?.onClick(dialog, which)
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                cancel?.onClick(dialog, which)
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }
}