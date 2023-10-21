package com.microtechmd.equil.ui.dlg;

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.rx.bus.RxBus
import com.microtechmd.equil.databinding.EquilAutoDressingDialogBinding
import com.microtechmd.equil.databinding.EquilDialogAlertDressingBinding
import com.microtechmd.equil.databinding.EquilPairConfigDialogBinding
import com.microtechmd.equil.events.EventEquilDlgChanged
import dagger.android.support.DaggerDialogFragment;
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class EquilAutoDressingDlg : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var ctx: Context
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var rxBus: RxBus

    private var _binding: EquilAutoDressingDialogBinding? = null

    val binding get() = _binding!!
    val disposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        _binding = EquilAutoDressingDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnCancel.setOnClickListener {
            binding.btnCancel?.let { onDialogResultListener?.invoke() }

            dismiss()
        }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        disposable.clear()
    }

    var task: Runnable? = null

    @Synchronized
    fun updateGUI(from: String) {
        if (_binding == null) return
        aapsLogger.debug("UpdateGUI from $from")

    }

    fun onClick(v: View): Boolean {
        return false
    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage ?: e.toString())
        }
    }

    fun setDialogResultListener(listener: () -> Unit) {
        onDialogResultListener = listener

    }

    private var onDialogResultListener: (() -> Unit)? = null

}
