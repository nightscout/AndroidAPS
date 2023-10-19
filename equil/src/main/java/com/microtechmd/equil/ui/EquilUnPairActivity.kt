package com.microtechmd.equil.ui

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.SystemClock
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.microtechmd.equil.EquilConst
import com.microtechmd.equil.EquilPumpPlugin
import com.microtechmd.equil.R
import com.microtechmd.equil.data.database.EquilHistoryRecord
import com.microtechmd.equil.databinding.EquilUnpairActivityBinding
import com.microtechmd.equil.events.EventEquilUnPairChanged
import com.microtechmd.equil.manager.command.*
import com.microtechmd.equil.ui.dlg.LoadingDlg
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.common.ble.BlePreCheck
import info.nightscout.androidaps.queue.Callback
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilUnPairActivity : DaggerAppCompatActivity() {

    @Inject lateinit var sp: SP
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var context: Context
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var equilPumpPlugin: EquilPumpPlugin
    @Inject lateinit var rxBus: RxBus

    private lateinit var binding: EquilUnpairActivityBinding
    @Inject lateinit var profileFunction: ProfileFunction
    var errorCount = 0;
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = EquilUnpairActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = rh.gs(R.string.equil_change)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        var name = equilPumpPlugin.equilManager.serialNumber
        binding.tvHint2.text = String.format(rh.gs(R.string.equil_unpair), name)
        binding.btnNext.setOnClickListener {
            showUnPairConfig();
        }
        binding.btnDelete.visibility = View.GONE
        binding.btnDelete.setOnClickListener {
            rxBus.send(EventEquilUnPairChanged())
            equilPumpPlugin.clearData()
            SystemClock.sleep(50)
            finish()
        }
    }

    private fun showUnPairConfig() {
        var name = equilPumpPlugin.equilManager.serialNumber
        var content = String.format(rh.gs(R.string.equil_unpair_alert), name)
        val alertDialog = AlertDialog.Builder(this)
            .setTitle(rh.gs(R.string.equil_title_tips))
            .setMessage(content)
            .setPositiveButton(rh.gs(R.string.ok)) { dialog: DialogInterface, _: Int ->
                unpair(name)
            }
            .setNegativeButton(rh.gs(R.string.cancel)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .create()
        alertDialog.show()

    }

    fun unpair(name: String) {
        showLoading();
        commandQueue.customCommand(CmdUnPair(name, sp.getString(rh.gs(R.string.key_equil_pair_password), "")), object : Callback() {
            override fun run() {
                dismissLoading();
                if (result.success) {
                    equilPumpPlugin.equilManager.serialNumber = ""
                    equilPumpPlugin.equilManager.address = ""
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_unbind))
                    rxBus.send(EventEquilUnPairChanged())
                    equilPumpPlugin.clearData()
                    SystemClock.sleep(50)
                    finish()
                } else {
                    errorCount += 1;
                    dismissLoading()
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                    if (errorCount > 5) {
                        runOnUiThread {
                            binding.btnDelete.visibility = View.VISIBLE
                        }
                    }
                }
            }
        })
    }

    private fun showLoading() {
        LoadingDlg().also { dialog ->
        }.show(supportFragmentManager, "loading")
    }

    private fun dismissLoading() {
        val fragment = supportFragmentManager.findFragmentByTag("loading")
        if (fragment is DialogFragment) {
            fragment.dismiss()
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

}
