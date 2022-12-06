package info.nightscout.automation.dialogs

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import androidx.fragment.app.FragmentManager
import dagger.android.support.DaggerDialogFragment
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

abstract class BaseDialog : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger

    //one shot guards
    private var okClicked: AtomicBoolean = AtomicBoolean(false)

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        aapsLogger.debug(LTag.UI, "Dialog opened: ${this.javaClass.simpleName}")
    }

    fun onCreateViewGeneral() {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (view.findViewById(info.nightscout.core.ui.R.id.ok) as Button?)?.setOnClickListener {
            synchronized(okClicked) {
                if (okClicked.get()) {
                    aapsLogger.warn(LTag.UI, "guarding: ok already clicked for dialog: ${this.javaClass.simpleName}")
                } else {
                    okClicked.set(true)
                    if (submit()) {
                        aapsLogger.debug(LTag.UI, "Submit pressed for Dialog: ${this.javaClass.simpleName}")
                        dismiss()
                    } else {
                        aapsLogger.debug(LTag.UI, "Submit returned false for Dialog: ${this.javaClass.simpleName}")
                        okClicked.set(false)
                    }
                }
            }
        }
        (view.findViewById(info.nightscout.core.ui.R.id.cancel) as Button?)?.setOnClickListener {
            aapsLogger.debug(LTag.UI, "Cancel pressed for dialog: ${this.javaClass.simpleName}")
            dismiss()
        }

    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage ?: "")
        }
    }

    abstract fun submit(): Boolean
}
