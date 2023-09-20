package com.microtechmd.equil.ui.pair

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.microtechmd.equil.R
import com.microtechmd.equil.data.database.EquilHistoryRecord
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.extensions.runOnUiThread
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilPairConfirmFragment : EquilPairFragmentBase() {

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_confirm_fragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ////        pumpSync.insertTherapyEventIfNewWithTimestamp(System.currentTimeMillis(),
        // //                DetailedBolusInfo.EventType.CANNULA_CHANGE, null, null, PumpType.EQUIL,
        // //                serialNumber());

        view.findViewById<Button>(R.id.button_next).setOnClickListener {
            context?.let {
                if ((activity as? EquilPairActivity)?.pair == true) {
                    equilPumpPlugin.pumpSync.connectNewPump()
                    equilPumpPlugin.pumpSync.insertTherapyEventIfNewWithTimestamp(
                        timestamp = System.currentTimeMillis(),
                        type = DetailedBolusInfo.EventType.CANNULA_CHANGE,
                        pumpType = PumpType.EQUIL,
                        pumpSerial = equilPumpPlugin.serialNumber()
                    )
                    equilPumpPlugin.pumpSync.insertTherapyEventIfNewWithTimestamp(
                        timestamp = System.currentTimeMillis(),
                        type = DetailedBolusInfo.EventType.INSULIN_CHANGE,
                        pumpType = PumpType.EQUIL,
                        pumpSerial = equilPumpPlugin.serialNumber()
                    )

                }
                var time = System.currentTimeMillis();
                val equilHistoryRecord = EquilHistoryRecord(
                    time,
                    null,
                    null,
                    EquilHistoryRecord.EventType.INSERT_CANNULA,
                    time,
                    equilPumpPlugin.serialNumber()
                )
                equilPumpPlugin.loopHandler.post {
                    equilPumpPlugin.equilHistoryRecordDao.insert(equilHistoryRecord)
                }
                equilPumpPlugin.equilManager.lastDataTime = System.currentTimeMillis()
                equilPumpPlugin.pumpSync.insertTherapyEventIfNewWithTimestamp(
                    System.currentTimeMillis(),
                    DetailedBolusInfo.EventType.CANNULA_CHANGE, null, null, PumpType.EQUIL,
                    equilPumpPlugin.serialNumber()
                );
                activity?.finish()
            }
        }
    }

    override fun getNextPageActionId(): Int? {
        return null;
    }

    override fun getIndex(): Int {
        if ((activity as? EquilPairActivity)?.pair == false) {
            return 2
        }
        return 4;
    }

}
