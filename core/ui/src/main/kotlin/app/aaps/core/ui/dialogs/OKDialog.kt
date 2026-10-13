package app.aaps.core.ui.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.os.SystemClock
import android.text.Spanned
import androidx.fragment.app.FragmentActivity
import app.aaps.core.ui.R
import app.aaps.core.ui.extensions.runOnUiThread
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object OKDialog {

    @SuppressLint("InflateParams")
    fun show(context: Context, title: String, message: String, runOnDismiss: Boolean = false, runnable: Runnable? = null) {
        var okClicked = false
        var notEmptyTitle = title
        if (notEmptyTitle.isEmpty()) notEmptyTitle = context.getString(R.string.message)

        MaterialAlertDialogBuilder(context, R.style.DialogTheme)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, notEmptyTitle))
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
            .setOnDismissListener {
                if (runOnDismiss) {
                    runOnUiThread(runnable)
                }
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @SuppressLint("InflateParams")
    fun show(context: Context, title: String, message: Spanned, runOnDismiss: Boolean = false, runnable: Runnable? = null) {
        var okClicked = false
        var notEmptyTitle = title
        if (notEmptyTitle.isEmpty()) notEmptyTitle = context.getString(R.string.message)

        MaterialAlertDialogBuilder(context, R.style.DialogTheme)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, notEmptyTitle))
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
            .setOnDismissListener {
                if (runOnDismiss) {
                    runOnUiThread(runnable)
                }
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }

    @SuppressLint("InflateParams")
    fun show(activity: FragmentActivity, title: String, message: Spanned, runOnDismiss: Boolean = false, runnable: Runnable? = null) {
        var okClicked = false
        var notEmptyTitle = title
        if (notEmptyTitle.isEmpty()) notEmptyTitle = activity.getString(R.string.message)

        MaterialAlertDialogBuilder(activity, R.style.DialogTheme)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(activity, notEmptyTitle))
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
            .setOnDismissListener {
                if (runOnDismiss) {
                    runOnUiThread(runnable)
                }
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }

    fun showConfirmation(activity: FragmentActivity, message: String, ok: Runnable?) {
        showConfirmation(activity, activity.getString(R.string.confirmation), message, ok, null)
    }

    fun showConfirmation(activity: FragmentActivity, message: Spanned, ok: Runnable?) {
        showConfirmation(activity, activity.getString(R.string.confirmation), message, ok, null)
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(activity: FragmentActivity, title: String, message: Spanned, ok: Runnable?, cancel: Runnable? = null) {
        var okClicked = false
        MaterialAlertDialogBuilder(activity, R.style.DialogTheme)
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
        MaterialAlertDialogBuilder(activity, R.style.DialogTheme)
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
        MaterialAlertDialogBuilder(context, R.style.DialogTheme)
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

    fun showConfirmation(context: Context, message: String, ok: Runnable?, cancel: Runnable? = null) {
        showConfirmation(context, context.getString(R.string.confirmation), message, ok, cancel)
    }

    @SuppressLint("InflateParams")
    fun showConfirmation(context: Context, title: String, message: String, ok: Runnable?, cancel: Runnable? = null) {
        var okClicked = false
        MaterialAlertDialogBuilder(context, R.style.DialogTheme)
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
    fun showConfirmation(context: Context, title: String, message: String, ok: DialogInterface.OnClickListener?, cancel: DialogInterface.OnClickListener? = null) {
        var okClicked = false
        MaterialAlertDialogBuilder(context, R.style.DialogTheme)
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

    @SuppressLint("InflateParams")
    fun showYesNoCancel(context: Context, title: String, message: String, yes: Runnable?, no: Runnable? = null) {
        var okClicked = false
        MaterialAlertDialogBuilder(context, R.style.DialogTheme)
            .setMessage(message)
            .setCustomTitle(AlertDialogHelper.buildCustomTitle(context, title))
            .setPositiveButton(R.string.yes) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setPositiveButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(yes)
                }
            }
            .setNegativeButton(R.string.no) { dialog: DialogInterface, _: Int ->
                if (okClicked) return@setNegativeButton
                else {
                    okClicked = true
                    dialog.dismiss()
                    SystemClock.sleep(100)
                    runOnUiThread(no)
                }
            }
            .setNeutralButton(R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
            .setCanceledOnTouchOutside(false)
    }

}
