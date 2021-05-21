package info.nightscout.androidaps.plugins.pump.insight.activities

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.insight.databinding.ActivityInsightPairingInformationBinding
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService

class InsightPairingInformationActivity : NoSplashAppCompatActivity() {

    private lateinit var binding: ActivityInsightPairingInformationBinding

    private var connectionService: InsightConnectionService? = null

    private val serviceConnection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val newConnectionService: InsightConnectionService = (binder as InsightConnectionService.LocalBinder).service
            if (!newConnectionService.isPaired) {
                overridePendingTransition(0, 0)
                finish()
                startActivity(Intent(this@InsightPairingInformationActivity, InsightPairingActivity::class.java))
            } else {
                binding.serialNumber.text = newConnectionService.pumpSystemIdentification.serialNumber
                binding.manufacturingDate.text = newConnectionService.pumpSystemIdentification.manufacturingDate
                binding.systemIdAppendix.text = newConnectionService.pumpSystemIdentification.systemIdAppendix.toString() + ""
                binding.releaseSwVersion.text = newConnectionService.pumpFirmwareVersions.releaseSWVersion
                binding.uiProcSwVersion.text = newConnectionService.pumpFirmwareVersions.uiProcSWVersion
                binding.pcProcSwVersion.text = newConnectionService.pumpFirmwareVersions.pcProcSWVersion
                binding.mdTelSwVersion.text = newConnectionService.pumpFirmwareVersions.mdTelProcSWVersion
                binding.safetyProcSwVersion.text = newConnectionService.pumpFirmwareVersions.safetyProcSWVersion
                binding.btInfoPageVersion.text = newConnectionService.pumpFirmwareVersions.btInfoPageVersion
                binding.bluetoothAddress.text = newConnectionService.bluetoothAddress
            }
            connectionService = newConnectionService
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connectionService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsightPairingInformationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindService(Intent(this, InsightConnectionService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        unbindService(serviceConnection)
        super.onDestroy()
    }

    fun deletePairing() {
        if (connectionService != null) {
            connectionService!!.reset()
            finish()
        }
    }
}