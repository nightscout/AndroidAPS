package app.aaps.pump.equil.ui.pair

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import androidx.navigation.fragment.findNavController
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.R
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.database.ResolvedResult
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.manager.command.CmdInsulinGet
import app.aaps.pump.equil.manager.command.CmdModelSet
import app.aaps.pump.equil.manager.command.CmdSettingSet

class EquilPairConfirmFragment : EquilPairFragmentBase() {

    override fun getLayoutId(): Int = R.layout.equil_pair_confirm_fragment

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.button_next).setOnClickListener {
            getCurrentInsulin()
        }
    }

    fun toSave() {
        context?.let {
            if ((activity as? EquilPairActivity)?.pair == true) {
                pumpSync.connectNewPump()
                pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = System.currentTimeMillis(),
                    type = TE.Type.CANNULA_CHANGE,
                    pumpType = PumpType.EQUIL,
                    pumpSerial = equilPumpPlugin.serialNumber()
                )
                pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = System.currentTimeMillis(),
                    type = TE.Type.INSULIN_CHANGE,
                    pumpType = PumpType.EQUIL,
                    pumpSerial = equilPumpPlugin.serialNumber()
                )

            }
            val time = System.currentTimeMillis()
            val equilHistoryRecord = EquilHistoryRecord(
                id = time,
                type = EquilHistoryRecord.EventType.INSERT_CANNULA,
                timestamp = time,
                serialNumber = equilPumpPlugin.serialNumber(),
                resolvedAt = System.currentTimeMillis(),
                resolvedStatus = ResolvedResult.SUCCESS
            )
            equilPumpPlugin.handler?.post {
                equilHistoryRecordDao.insert(equilHistoryRecord)
            }
            equilManager.setLastDataTime(System.currentTimeMillis())
            pumpSync.insertTherapyEventIfNewWithTimestamp(
                System.currentTimeMillis(),
                TE.Type.CANNULA_CHANGE, null, null, PumpType.EQUIL,
                equilPumpPlugin.serialNumber()
            )
            equilManager.setActivationProgress(ActivationProgress.COMPLETED)
            activity?.finish()
        }
    }

    override fun getNextPageActionId(): Int? = null

    override fun getIndex(): Int = 6

    private fun setModel() {
        showLoading()
        commandQueue.customCommand(CmdModelSet(RunMode.RUN.command, aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.debug(LTag.PUMPCOMM, "setModel result====" + result.success + "====")
                dismissLoading()
                if (result.success) {
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    setLimits()
                } else ToastUtils.errorToast(requireContext(), rh.gs(R.string.equil_error))
            }
        })
    }

    private fun setLimits() {
        showLoading()
        val profile = pumpSync.expectedPumpState().profile ?: return
        commandQueue.customCommand(CmdSettingSet(constraintsChecker.getMaxBolusAllowed().value(), constraintsChecker.getMaxBasalAllowed(profile).value(), aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.debug(LTag.PUMPCOMM, "setLimits result====" + result.success + "====")
                dismissLoading()
                if (result.success) {
                    equilManager.setRunMode(RunMode.RUN)
                    toSave()
                } else ToastUtils.errorToast(requireContext(), rh.gs(R.string.equil_error))
            }
        })
    }
    private fun getCurrentInsulin() {
        showLoading()
        commandQueue.customCommand(CmdInsulinGet(aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                dismissLoading()
                if (result.success) {
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    setModel()
                } else ToastUtils.errorToast(requireContext(), rh.gs(R.string.equil_error))
            }
        })
    }
}
