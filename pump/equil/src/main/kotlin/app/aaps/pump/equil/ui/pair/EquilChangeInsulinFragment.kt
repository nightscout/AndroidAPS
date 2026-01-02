package app.aaps.pump.equil.ui.pair

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.navigation.fragment.findNavController
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.equil.R
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.manager.command.CmdInsulinChange
import com.bumptech.glide.Glide

class EquilChangeInsulinFragment : EquilPairFragmentBase() {

    override fun getLayoutId(): Int = R.layout.equil_pair_change_insulin_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Glide.with(view)
            .asGif()
            .load(R.drawable.equil_animation_wizard_detach)
            .into(view.findViewById(R.id.imv))

        view.findViewById<Button>(R.id.button_next).setOnClickListener {
            showLoading()
            commandQueue.customCommand(CmdInsulinChange(aapsLogger, preferences, equilManager), object : Callback() {
                override fun run() {
                    dismissLoading()
                    if (result.success) {
                        equilPumpPlugin.resetData()
                        equilManager.setRunMode(RunMode.STOP)
                        runOnUiThread {
                            if (isAdded) {
                                val nextPage = getNextPageActionId()
                                findNavController().navigate(nextPage)
                            }
                        }
                    } else {
                        dismissLoading()
                        ToastUtils.errorToast(requireContext(), rh.gs(R.string.equil_error))
                    }
                }
            })
        }
    }

    override fun getNextPageActionId(): Int = R.id.action_startEquilChangeInsulinFragment_to_startEquilPairAssembleFragment
    override fun getIndex(): Int = 1
}
