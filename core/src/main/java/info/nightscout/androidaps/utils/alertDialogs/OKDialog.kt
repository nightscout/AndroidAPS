package info.nightscout.androidaps.utils.alertDialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.text.Spanned
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.utils.extensions.runOnUiThread
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Provider

object OKDialog {
    @SuppressLint("InflateParams")
    fun show(context: Context, title: String, message: String, runnable: Runnable? = null , sp: SP? = null) {
        var okClicked = false
        var notEmptytitle = title
        if (notEmptytitle.isEmpty()) notEmptytitle = context.getString(R.string.message)

        val adb: AlertDialog.Builder = AlertDialog.Builder(context)
        adb
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, notEmptytitle))
            .setMessage(message)
            .setPositiveButton(context.getString(R.string.ok)) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(runnable)
                }
            }
        val alertdialog = adb.create()
        alertdialog.setCanceledOnTouchOutside(false)
        alertdialog.setOnShowListener { setdrawableBackground(context, alertdialog, sp) }
        alertdialog.show()

    }

    fun setdrawableBackground(context: Context, alertdialog: AlertDialog, sp: SP? = null) {
        val drawable: Drawable? = context.let { ContextCompat.getDrawable(it, R.drawable.dialog) }
        if (sp != null) {
            if ( sp.getBoolean("daynight", true)) {
                if (drawable != null) {
                    drawable.setColorFilter(sp.getInt("darkBackgroundColor", R.color.background_dark), PorterDuff.Mode.SRC_IN)
                }
            } else {
                if (drawable != null) {
                    drawable.setColorFilter(sp.getInt("lightBackgroundColor", R.color.background_light), PorterDuff.Mode.SRC_IN)
                }
            }
        }
        alertdialog.window?.setBackgroundDrawable(drawable)
    }

    @SuppressLint("InflateParams")
    fun show(activity: Activity, title: String, message: Spanned, runnable: Runnable? = null, sp: SP? = null) {
        var okClicked = false
        var notEmptytitle = title
        if (notEmptytitle.isEmpty()) notEmptytitle = activity.getString(R.string.message)

        val adb: AlertDialog.Builder =  AlertDialog.Builder(ContextThemeWrapper(activity, ThemeUtil.getActualTheme()))
        adb
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, notEmptytitle))
            .setMessage(message)
            .setPositiveButton(activity.getString(R.string.ok)) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runnable?.let { activity.runOnUiThread(it) }
                }
            }
        val alertdialog = adb.create()
        alertdialog.setCanceledOnTouchOutside(false)
        alertdialog.setOnShowListener { setdrawableBackground(activity.baseContext, alertdialog, sp) }
        alertdialog.show()
    }

    @JvmStatic
    fun showConfirmation(activity: Activity, message: String, ok: Runnable?, sp: SP? = null) {
        showConfirmation(activity, activity.getString(R.string.confirmation), message, ok, null, sp)
    }

    @JvmStatic
    fun showConfirmation(activity: Activity, message: Spanned, ok: Runnable?, sp: SP? = null) {
        showConfirmation(activity, activity.getString(R.string.confirmation), message, ok, null, sp)
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(activity: Activity, title: String, message: Spanned, ok: Runnable?, cancel: Runnable? = null, sp: SP? = null) {
        var okClicked = false
        val adb: AlertDialog.Builder =  AlertDialogHelper.Builder(activity)
        adb
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, title))
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    ok?.let { activity.runOnUiThread(it) }
                }
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    cancel?.let { activity.runOnUiThread(it) }
                }
            }
        val alertdialog = adb.create()
        alertdialog.setCanceledOnTouchOutside(false)
        alertdialog.setOnShowListener { setdrawableBackground(activity.baseContext, alertdialog, sp) }
        alertdialog.show()
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(activity: Activity, title: String, message: String, ok: Runnable?, cancel: Runnable? = null, sp: SP? = null) {
        var okClicked = false
        val adb: AlertDialog.Builder =  AlertDialogHelper.Builder(activity)
        adb
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, title))
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    ok?.let { activity.runOnUiThread(it) }
                }
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    cancel?.let { activity.runOnUiThread(it) }
                }
            }
        val alertdialog = adb.create()
        alertdialog.setCanceledOnTouchOutside(false)
        alertdialog.setOnShowListener { setdrawableBackground(activity.baseContext, alertdialog, sp) }
        alertdialog.show()
    }

    fun showConfirmation(context: Context, message: Spanned, ok: Runnable?, cancel: Runnable? = null,  sp: SP? = null) {
        showConfirmation(context, context.getString(R.string.confirmation), message, ok, cancel, sp)
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(context: Context, title: String, message: Spanned, ok: Runnable?, cancel: Runnable? = null, sp: SP? = null) {
        var okClicked = false
        val adb: AlertDialog.Builder =  AlertDialogHelper.Builder(context)
        adb
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title))
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(ok)
                }
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(cancel)
                }
            }
        val alertdialog = adb.create()
        alertdialog.setCanceledOnTouchOutside(false)
        alertdialog.setOnShowListener { setdrawableBackground(context, alertdialog, sp) }
        alertdialog.show()
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, message: String, ok: Runnable?, cancel: Runnable? = null, sp: SP? = null) {
        showConfirmation(context, context.getString(R.string.confirmation), message, ok, cancel, sp)
    }

    @SuppressLint("InflateParams")
    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, title: String, message: String, ok: Runnable?, cancel: Runnable? = null, sp: SP? = null) {
        var okClicked = false
        val adb: AlertDialog.Builder =  AlertDialogHelper.Builder(context)
        adb
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title))
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(ok)
                }
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(cancel)
                }
            }
        val alertdialog = adb.create()
        alertdialog.setCanceledOnTouchOutside(false)
        alertdialog.setOnShowListener { setdrawableBackground(context, alertdialog, sp) }
        alertdialog.show()
    }

    @SuppressLint("InflateParams")
    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, title: String, message: String, ok: DialogInterface.OnClickListener?, cancel: DialogInterface.OnClickListener? = null, sp: SP? = null) {
        var okClicked = false
        val adb: AlertDialog.Builder =  AlertDialogHelper.Builder(context)
        adb
        AlertDialogHelper.Builder(context)
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title))
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, which: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    ok?.onClick(dialog, which)
                }
            }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, which: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    cancel?.onClick(dialog, which)
                }
            }
        val alertdialog = adb.create()
        alertdialog.setCanceledOnTouchOutside(false)
        alertdialog.setOnShowListener { setdrawableBackground(context, alertdialog, sp) }
        alertdialog.show()
    }
}