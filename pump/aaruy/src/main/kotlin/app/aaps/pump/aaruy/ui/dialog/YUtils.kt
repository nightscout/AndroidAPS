package app.aaps.pump.aaruy.ui.dialog

import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.Window
import android.widget.TextView
import app.aaps.pump.aaruy.R

object YUtils {
    private var lastDialog: Dialog? = null

    fun showLoading(context: Context, message: String) {
        lastDialog = Dialog(context, R.style.dialog)
        lastDialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        lastDialog!!.setCancelable(false)
        lastDialog!!.setCanceledOnTouchOutside(false)
        lastDialog!!.setContentView(R.layout.circle_dialog)
        lastDialog!!.getWindow()?.setBackgroundDrawableResource(android.R.color.transparent)
        val msgTv = lastDialog!!.findViewById<TextView>(R.id.id_tv_loadingmsg)
        if (message.isEmpty()) {
            msgTv.visibility = View.GONE
        } else {
            msgTv.visibility = View.VISIBLE
        }
        msgTv.setText(message)
        lastDialog!!.show()
    }

    /**
     * loading是否显示，需在showLoading()之后调用，否则为false
     */
    fun loadingIsShowing(): Boolean {
        try {
            return lastDialog?.isShowing ?: false
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * hideLoading
     */
    fun hideLoading() {
        try {
            if (lastDialog != null && lastDialog!!.isShowing) {
                lastDialog!!.dismiss()
                lastDialog = null
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
}