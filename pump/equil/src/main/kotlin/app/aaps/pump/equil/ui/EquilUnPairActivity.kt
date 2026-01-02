package app.aaps.pump.equil.ui

import android.os.Bundle
import androidx.fragment.app.DialogFragment
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.R
import app.aaps.pump.equil.databinding.EquilUnpairActivityBinding
import app.aaps.pump.equil.events.EventEquilUnPairChanged
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.command.CmdUnPair
import app.aaps.pump.equil.ui.dlg.LoadingDlg
import javax.inject.Inject

class EquilUnPairActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var equilPumpPlugin: EquilPumpPlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var equilManager: EquilManager

    private lateinit var binding: EquilUnpairActivityBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = EquilUnpairActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = rh.gs(R.string.equil_change)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        val name = equilManager.equilState?.serialNumber
        binding.tvHint2.text = String.format(rh.gs(R.string.equil_unpair), name)
        binding.btnFinish.setOnClickListener {
            val name = equilManager.equilState?.serialNumber ?: throw IllegalStateException()
            OKDialog.showConfirmation(
                this, rh.gs(app.aaps.core.ui.R.string.confirmation), rh.gs(R.string.equil_unpair_alert, name),
                { unpair(name) }
            )
        }
    }

    private fun unpair(name: String) {
        showLoading()
        commandQueue.customCommand(CmdUnPair(name, preferences.get(EquilStringKey.PairPassword), aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                dismissLoading()
                val title = if (!result.success) rh.gs(R.string.equil_error) else rh.gs(R.string.equil_success)
                val message = if (!result.success) rh.gs(R.string.equil_removed_anyway) else rh.gs(R.string.equil_device_unpaired)
                runOnUiThread {
                    OKDialog.show(this@EquilUnPairActivity, title, message, true) {
                        equilManager.setSerialNumber("")
                        equilManager.setAddress("")
                        rxBus.send(EventEquilUnPairChanged())
                        equilPumpPlugin.clearData()
                        finish()
                    }
                }
            }
        })
    }

    private fun showLoading() {
        LoadingDlg().show(supportFragmentManager, "loading")
    }

    private fun dismissLoading() {
        supportFragmentManager.findFragmentByTag("loading")?.let { fragment ->
            if (fragment is DialogFragment) {
                try {
                    fragment.dismiss()
                } catch (e: IllegalStateException) {
                    // dialog not running yet
                    aapsLogger.error("Unhandled exception", e)
                }
            }
        }
    }
}
