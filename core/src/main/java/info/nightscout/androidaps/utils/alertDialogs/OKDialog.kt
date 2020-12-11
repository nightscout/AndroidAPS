package info.nightscout.androidaps.utils.alertDialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.SystemClock
import android.text.Spanned
import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.extensions.runOnUiThread

object OKDialog {
    @SuppressLint("InflateParams")
    fun show(context: Context, title: String, message: String, runnable: Runnable? = null) {
        var okClicked = false
        var notEmptytitle = title
        if (notEmptytitle.isEmpty()) notEmptytitle = context.getString(R.string.message)

        AlertDialogHelper.Builder(context)
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
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @SuppressLint("InflateParams")
    fun show(activity: FragmentActivity, title: String, message: Spanned, runnable: Runnable? = null) {
        var okClicked = false
        var notEmptytitle = title
        if (notEmptytitle.isEmpty()) notEmptytitle = activity.getString(R.string.message)

        AlertDialogHelper.Builder(activity)
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
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @JvmStatic
    fun showConfirmation(activity: FragmentActivity, message: String, ok: Runnable?) {
        showConfirmation(activity, activity.getString(R.string.confirmation), message, ok, null)
    }

    @JvmStatic
    fun showConfirmation(activity: FragmentActivity, message: Spanned, ok: Runnable?) {
        showConfirmation(activity, activity.getString(R.string.confirmation), message, ok, null)
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(activity: FragmentActivity, title: String, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        var okClicked = false
        AlertDialogHelper.Builder(activity)
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
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(activity: FragmentActivity, title: String, message: String, ok: Runnable?, cancel: Runnable? = null) {
        var okClicked = false
        AlertDialogHelper.Builder(activity)
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
            .show()
            .setCanceledOnTouchOutside(false)
    }

    fun showConfirmation(context: Context, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        showConfirmation(context, context.getString(R.string.confirmation), message, ok, cancel)
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(context: Context, title: String, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        var okClicked = false
        AlertDialogHelper.Builder(context)
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
        var okClicked = false
        AlertDialogHelper.Builder(context)
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
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @SuppressLint("InflateParams")
    @JvmStatic
    @JvmOverloads
    fun showConfirmation(context: Context, title: String, message: String, ok: DialogInterface.OnClickListener?, cancel: DialogInterface.OnClickListener? = null) {
        var okClicked = false
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
            .show()
            .setCanceledOnTouchOutside(false)
    }
}