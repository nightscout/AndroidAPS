package app.aaps.pump.equil.ui.pair

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import app.aaps.core.data.model.TE
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.Callback
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.R
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.data.database.EquilHistoryRecord
import app.aaps.pump.equil.data.database.ResolvedResult
import app.aaps.pump.equil.driver.definition.ActivationProgress
import app.aaps.pump.equil.manager.command.CmdInsulinGet
import app.aaps.pump.equil.manager.command.CmdModelSet

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilPairConfirmFragment : EquilPairFragmentBase() {

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_confirm_fragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.button_next).setOnClickListener {
            getCurrentInsulin()
        }
    }

    fun toSave() {
        context?.let {
            if ((activity as? EquilPairActivity)?.pair == true) {
                equilPumpPlugin.pumpSync.connectNewPump()
                equilPumpPlugin.pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = System.currentTimeMillis(),
                    type = TE.Type.CANNULA_CHANGE,
                    pumpType = PumpType.EQUIL,
                    pumpSerial = equilPumpPlugin.serialNumber()
                )
                equilPumpPlugin.pumpSync.insertTherapyEventIfNewWithTimestamp(
                    timestamp = System.currentTimeMillis(),
                    type = TE.Type.INSULIN_CHANGE,
                    pumpType = PumpType.EQUIL,
                    pumpSerial = equilPumpPlugin.serialNumber()
                )

            }
            val time = System.currentTimeMillis()
            val equilHistoryRecord = EquilHistoryRecord(
                time,
                null,
                null,
                EquilHistoryRecord.EventType.INSERT_CANNULA,
                time,
                equilPumpPlugin.serialNumber()
            )
            equilHistoryRecord.resolvedAt = System.currentTimeMillis()
            equilHistoryRecord.resolvedStatus = ResolvedResult.SUCCESS
            equilPumpPlugin.loopHandler.post {
                equilPumpPlugin.equilHistoryRecordDao.insert(equilHistoryRecord)
            }
            equilPumpPlugin.equilManager.lastDataTime = System.currentTimeMillis()
            equilPumpPlugin.pumpSync.insertTherapyEventIfNewWithTimestamp(
                System.currentTimeMillis(),
                TE.Type.CANNULA_CHANGE, null, null, PumpType.EQUIL,
                equilPumpPlugin.serialNumber()
            )
            equilPumpPlugin.equilManager.activationProgress = ActivationProgress.COMPLETED
            activity?.finish()
        }
    }

    override fun getNextPageActionId(): Int? {
        return null
    }

    override fun getIndex(): Int {
        return 6
    }

    private fun setModel() {
        showLoading()
        commandQueue.customCommand(CmdModelSet(RunMode.RUN.command), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.debug(LTag.PUMPCOMM, "setModel result====" + result.success + "====")
                if (result.success) {
                    dismissLoading()
                    equilPumpPlugin.equilManager.runMode = RunMode.RUN
                    toSave()
                } else {
                    dismissLoading()
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }

    private fun getCurrentInsulin() {
        showLoading()
        commandQueue.customCommand(CmdInsulinGet(), object : Callback() {
            override fun run() {
                if (activity == null) return
                if (result.success) {
                    if (activity == null)
                        return
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    dismissLoading()
                    setModel()
                } else {
                    dismissLoading()
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }
}
