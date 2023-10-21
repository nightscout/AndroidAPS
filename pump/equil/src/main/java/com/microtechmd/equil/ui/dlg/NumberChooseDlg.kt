package com.microtechmd.equil.ui.dlg;

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import com.microtechmd.equil.R
import com.microtechmd.equil.data.AlarmMode
import com.microtechmd.equil.databinding.EquilNumberChooiceDialogBinding
import com.microtechmd.equil.databinding.EquilSingleChooiceDialogBinding
import dagger.android.support.DaggerDialogFragment;
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class NumberChooseDlg : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var ctx: Context
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper

    private var _binding: EquilNumberChooiceDialogBinding? = null

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

        _binding = EquilNumberChooiceDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.lytAction.cancel.setOnClickListener { dismiss() }
        binding.lytAction.ok.setOnClickListener {

            binding.pickerAmount?.let { onDialogResultListener?.invoke(binding.pickerAmount.value.toFloat()) }
            dismiss()
        }
        binding.pickerAmount.setParams(10.0, 10.0, 100.0, 0.5, DecimalFormat("0.0"), false, null, object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }
        })
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

    fun setDialogResultListener(listener: (Float) -> Unit) {
        onDialogResultListener = listener

    }

    private var onDialogResultListener: ((Float) -> Unit)? = null

    interface OnDialogResultListener {
        fun onDialogResult(result: Float?)
    }

}
