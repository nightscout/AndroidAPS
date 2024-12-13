package app.aaps.pump.eopatch.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.eopatch.bindingadapters.setOnSafeClickListener
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.databinding.DialogCommonBinding
import app.aaps.pump.eopatch.ui.DialogHelperActivity
import app.aaps.pump.eopatch.ui.EopatchActivity
import app.aaps.pump.eopatch.vo.PatchConfig
import dagger.android.support.DaggerDialogFragment
import javax.inject.Inject

class ActivationNotCompleteDialog : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var patchManager: IPatchManager
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var patchConfig: PatchConfig

    var helperActivity: DialogHelperActivity? = null
    var message: String = ""
    var title: String = ""

    private var _binding: DialogCommonBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)

        savedInstanceState?.let { bundle ->
            bundle.getString("title")?.let { title = it }
            bundle.getString("message")?.let { message = it }
        }
        _binding = DialogCommonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title.text = title
        binding.ok.setOnSafeClickListener {
            helperActivity?.apply {
                startActivity(EopatchActivity.createIntent(this, patchConfig.lifecycleEvent.lifeCycle, false))
            }
            dismiss()
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putString("message", message)
        bundle.putString("title", title)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onResume() {
        super.onResume()
        binding.message.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun dismiss() {
        super.dismissAllowingStateLoss()
        helperActivity?.finish()
    }
}
