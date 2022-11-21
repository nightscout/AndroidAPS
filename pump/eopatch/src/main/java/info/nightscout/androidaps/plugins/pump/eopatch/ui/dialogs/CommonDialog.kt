package info.nightscout.androidaps.plugins.pump.eopatch.ui.dialogs

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.rx.logging.AAPSLogger
import javax.inject.Inject

class CommonDialog : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger

    var title: Int = 0
    var message: Int = 0
    var positiveBtn: Int = R.string.confirm
    var negativeBtn: Int = 0

    var positiveListener: DialogInterface.OnClickListener? = null
    var negativeListener: DialogInterface.OnClickListener? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let{
            val builder = info.nightscout.core.ui.dialogs.AlertDialogHelper.Builder(it).apply {
                if(title != 0) setTitle(title)
                if(message != 0) setMessage(message)
                setPositiveButton(positiveBtn,
                positiveListener?:DialogInterface.OnClickListener { _, _ ->
                    dismiss()
                })
                if(negativeBtn != 0) {
                    setNegativeButton(negativeBtn,
                        negativeListener ?: DialogInterface.OnClickListener { _, _ ->
                            dismiss()
                        })
                }
            }

            builder.create()
        } ?: throw IllegalStateException("Activity is null")
    }
}
