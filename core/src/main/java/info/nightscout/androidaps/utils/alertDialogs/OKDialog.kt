package info.nightscout.androidaps.utils.alertDialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.os.SystemClock
import android.text.Spanned
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.extensions.runOnUiThread

object OKDialog {
    @SuppressLint("InflateParams")
    fun show(context: Context, title: String, message: String, runnable: Runnable? = null) {
        var notEmptytitle = title
        if (notEmptytitle.isEmpty()) notEmptytitle = context.getString(R.string.message)

        AlertDialogHelper.Builder(context)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, notEmptytitle))
            .setMessage(message)
            .setPositiveButton(context.getString(R.string.ok)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                runOnUiThread(runnable)
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @SuppressLint("InflateParams")
    @JvmStatic
    @JvmOverloads
    fun show(activity: Activity, title: String, message: Spanned, runnable: Runnable? = null) {
        var notEmptytitle = title
        if (notEmptytitle.isEmpty()) notEmptytitle = activity.getString(R.string.message)

        AlertDialogHelper.Builder(activity)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, notEmptytitle))
            .setMessage(message)
            .setPositiveButton(activity.getString(R.string.ok)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                runnable?.let { activity.runOnUiThread(it) }
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @JvmStatic
    fun showConfirmation(activity: Activity, message: String, ok: Runnable?) {
        showConfirmation(activity, activity.getString(R.string.confirmation), message, ok, null)
    }

    @JvmStatic
    fun showConfirmation(activity: Activity, message: Spanned, ok: Runnable?) {
        showConfirmation(activity, activity.getString(R.string.confirmation), message, ok, null)
    }

    @SuppressLint("InflateParams")
    @JvmStatic
    @JvmOverloads
    fun showConfirmation(activity: Activity, title: String, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        AlertDialogHelper.Builder(activity)
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, title))
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

    @SuppressLint("InflateParams")
    @JvmStatic
    fun showConfirmation(activity: Activity, title: String, message: String, ok: Runnable?, cancel: Runnable? = null) {
        AlertDialogHelper.Builder(activity)
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, title))
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
        showConfirmation(context, context.getString(R.string.confirmation), message, ok, cancel)
    }

    @SuppressLint("InflateParams")
    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, title: String, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        AlertDialogHelper.Builder(context)
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title))
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
        showConfirmation(context, context.getString(R.string.confirmation), message, ok, cancel)
    }

    @SuppressLint("InflateParams")
    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, title: String, message: String, ok: Runnable?, cancel: Runnable? = null) {
        AlertDialogHelper.Builder(context)
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title))
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

    @SuppressLint("InflateParams")
    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, title: String, message: String, ok: DialogInterface.OnClickListener?, cancel: DialogInterface.OnClickListener? = null) {
        AlertDialogHelper.Builder(context)
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title))
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