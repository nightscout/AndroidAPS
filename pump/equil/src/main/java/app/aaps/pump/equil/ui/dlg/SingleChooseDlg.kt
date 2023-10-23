package app.aaps.pump.equil.ui.dlg;

import android.content.Context
import android.os.Bundle
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
import app.aaps.pump.equil.R
import app.aaps.pump.equil.data.AlarmMode
import app.aaps.pump.equil.databinding.EquilSingleChooiceDialogBinding
import dagger.android.support.DaggerDialogFragment;
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject

class SingleChooseDlg(val alarmMode: AlarmMode) : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var ctx: Context
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper

    private var _binding: EquilSingleChooiceDialogBinding? = null
    private var adapter: RecyclerViewAdapter? = null

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

        _binding = EquilSingleChooiceDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.lytAction.cancel.setOnClickListener { dismiss() }
        binding.lytAction.ok.setOnClickListener {
            adapter?.selectItem?.let { onDialogResultListener?.invoke(it) }
            dismiss()
        }
        val itemList = ArrayList<Item>()
        // equil_tone_mode_tone
        itemList.add(Item(AlarmMode.TONE, rh.gs(R.string.equil_tone_mode_tone)))
        itemList.add(Item(AlarmMode.SHAKE, rh.gs(R.string.equil_tone_mode_shake)))
        itemList.add(Item(AlarmMode.TONE_AND_SHAKE, rh.gs(R.string.equil_tone_mode_tone_and_shake)))
        itemList.add(Item(AlarmMode.MUTE, rh.gs(R.string.equil_tone_mode_mute)))
        adapter = RecyclerViewAdapter(itemList, alarmMode);
        binding.recyclerview.layoutManager = LinearLayoutManager(ctx)
        binding.recyclerview.swapAdapter(adapter, false)

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

    private fun onClickOkCancelEnabled(v: View): Boolean {
        var description = ""
        return true
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

    fun setDialogResultListener(listener: (Item) -> Unit) {
        onDialogResultListener = listener

    }

    private var onDialogResultListener: ((Item) -> Unit)? = null

    override fun onResume() {
        super.onResume()
    }

    interface OnDialogResultListener {

        fun onDialogResult(result: View?)
    }

    data class Item(val data: AlarmMode, val title: String)

    class RecyclerViewAdapter internal constructor(
        var itemList: List<Item>,
        val alarmMode: AlarmMode

    ) : RecyclerView.Adapter<RecyclerViewAdapter.HistoryViewHolder>() {

        fun setHistoryListInternal(itemList: List<Item>) {
            this.itemList = itemList
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): HistoryViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(
                R.layout.equil_item_chooice,  //
                viewGroup, false
            )
            return HistoryViewHolder(v)
        }

        var selectView: View? = null;
        var selectItem: Item? = null;

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val record = itemList[position]
            holder.nameView.text = record.title
            if (alarmMode.command == record.data.command) {
                holder.itemView.isSelected = true
                selectItem = record
                selectView = holder.itemView
            }
            holder.itemView.setOnClickListener { v ->
                v.isSelected = true
                selectItem = record
                if (selectView != null) {
                    selectView?.isSelected = false
                }
                selectView = v
            }
        }

        override fun getItemCount(): Int {
            return itemList.size
        }

        class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            var nameView: TextView
            // var valueView: TextView

            init {
                nameView = itemView.findViewById(R.id.tv_name)
            }
        }

    }
}
