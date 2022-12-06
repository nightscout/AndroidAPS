package info.nightscout.configuration.maintenance.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.TableLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import info.nightscout.configuration.R
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.interfaces.maintenance.Prefs
import info.nightscout.interfaces.maintenance.PrefsStatus
import info.nightscout.shared.extensions.runOnUiThread
import java.util.LinkedList

object PrefImportSummaryDialog {

    @SuppressLint("InflateParams")
    fun showSummary(context: Context, importOk: Boolean, importPossible: Boolean, prefs: Prefs, ok: (() -> Unit)?, cancel: (() -> Unit)? = null) {

        @StyleRes val theme: Int = if (importOk) info.nightscout.core.ui.R.style.DialogTheme else {
            if (importPossible) info.nightscout.core.ui.R.style.AppThemeWarningDialog else info.nightscout.core.ui.R.style.AppThemeErrorDialog
        }

        @StringRes val messageRes: Int = if (importOk) R.string.check_preferences_before_import else {
            if (importPossible) R.string.check_preferences_dangerous_import else R.string.check_preferences_cannot_import
        }

        @DrawableRes val headerIcon: Int = if (importOk) R.drawable.ic_header_import else {
            if (importPossible) info.nightscout.core.ui.R.drawable.ic_header_warning else R.drawable.ic_header_error
        }

        val themedCtx = ContextThemeWrapper(context, theme)

        val innerLayout = LayoutInflater.from(themedCtx).inflate(R.layout.dialog_alert_import_summary, null)
        val table = (innerLayout.findViewById<View>(R.id.summary_table) as TableLayout)
        val detailsBtn = (innerLayout.findViewById<View>(R.id.summary_details_btn) as Button)

        var idx = 0
        val details = LinkedList<String>()

        for ((metaKey, metaEntry) in prefs.metadata) {
            val rowLayout = LayoutInflater.from(themedCtx).inflate(R.layout.import_summary_item, null)
            val label = (rowLayout.findViewById<View>(R.id.summary_text) as TextView)
            label.text = metaKey.formatForDisplay(context, metaEntry.value)
            (rowLayout.findViewById<View>(R.id.summary_icon) as ImageView).setImageResource(metaKey.icon)
            (rowLayout.findViewById<View>(R.id.status_icon) as ImageView).setImageResource(metaEntry.status.icon)

            if (metaEntry.status == PrefsStatus.WARN) label.setTextColor(themedCtx.getColor(info.nightscout.interfaces.R.color.metadataTextWarning))
            else if (metaEntry.status == PrefsStatus.ERROR) label.setTextColor(themedCtx.getColor(info.nightscout.interfaces.R.color.metadataTextError))

            if (metaEntry.info != null) {
                details.add("<b>${context.getString(metaKey.label)}</b>: ${metaEntry.value}<br/><i style=\"color:silver\">${metaEntry.info}</i>")
                rowLayout.isClickable = true
                rowLayout.setOnClickListener {
                    val msg = "[${context.getString(metaKey.label)}] ${metaEntry.info}"
                    when (metaEntry.status) {
                        PrefsStatus.WARN  -> ToastUtils.Long.warnToast(context, msg)
                        PrefsStatus.ERROR -> ToastUtils.Long.errorToast(context, msg)
                        else              -> ToastUtils.Long.infoToast(context, msg)
                    }
                }
            } else {
                rowLayout.isClickable = true
                rowLayout.setOnClickListener { ToastUtils.Long.infoToast(context, context.getString(metaKey.label)) }
            }

            table.addView(rowLayout, idx++)
        }

        if (details.size > 0) {
            detailsBtn.visibility = View.VISIBLE
            detailsBtn.setOnClickListener {
                val detailsLayout = LayoutInflater.from(context).inflate(R.layout.import_summary_details, null)
                val webView = detailsLayout.findViewById<View>(R.id.details_webview) as WebView
                webView.loadData(
                    "<!doctype html><html><head><meta charset=\"utf-8\"><style>body { color: white; }</style></head><body>" + details.joinToString("<hr>"),
                    "text/html; charset=utf-8",
                    "utf-8"
                )
                webView.setBackgroundColor(Color.TRANSPARENT)
                webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)

                MaterialAlertDialogBuilder(context, info.nightscout.core.ui.R.style.DialogTheme)
                    .setCustomTitle(
                        info.nightscout.core.ui.dialogs.AlertDialogHelper.buildCustomTitle(
                            context,
                            context.getString(R.string.check_preferences_details_title),
                            R.drawable.ic_header_log,
                            info.nightscout.core.ui.R.style.AppTheme
                        )
                    )
                    .setView(detailsLayout)
                    .setPositiveButton(android.R.string.ok) { dialogInner: DialogInterface, _: Int ->
                        dialogInner.dismiss()
                    }
                    .show()
            }
        }

        val builder = MaterialAlertDialogBuilder(context, theme)
            .setMessage(context.getString(messageRes))
            .setCustomTitle(info.nightscout.core.ui.dialogs.AlertDialogHelper.buildCustomTitle(context, context.getString(R.string.import_setting), headerIcon, theme))
            .setView(innerLayout)
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                if (cancel != null) runOnUiThread { cancel() }
            }

        if (importPossible) {
            builder.setPositiveButton(
                if (importOk) R.string.check_preferences_import_btn else R.string.check_preferences_import_anyway_btn
            ) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                SystemClock.sleep(100)
                if (ok != null) runOnUiThread { ok() }
            }
        }

        val dialog = builder.show()
        val textView = dialog.findViewById<View>(android.R.id.message) as TextView?
        textView?.textSize = 12f
        textView?.setPadding(10, 0, 0, 0)
        dialog.setCanceledOnTouchOutside(false)
    }

}