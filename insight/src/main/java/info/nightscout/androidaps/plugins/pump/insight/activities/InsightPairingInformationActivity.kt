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
            if (!newConnectionService.isPaired()) {
                overridePendingTransition(0, 0)
                finish()
                startActivity(Intent(this@InsightPairingInformationActivity, InsightPairingActivity::class.java))
            } else {
                binding.serialNumber.text = newConnectionService.getPumpSystemIdentification().serialNumber
                binding.manufacturingDate.text = newConnectionService.getPumpSystemIdentification().manufacturingDate
                binding.systemIdAppendix.text = newConnectionService.getPumpSystemIdentification().systemIdAppendix.toString() + ""
                binding.releaseSwVersion.text = newConnectionService.getPumpFirmwareVersions().releaseSWVersion
                binding.uiProcSwVersion.text = newConnectionService.getPumpFirmwareVersions().uiProcSWVersion
                binding.pcProcSwVersion.text = newConnectionService.getPumpFirmwareVersions().pcProcSWVersion
                binding.mdTelSwVersion.text = newConnectionService.getPumpFirmwareVersions().mdTelProcSWVersion
                binding.safetyProcSwVersion.text = newConnectionService.getPumpFirmwareVersions().safetyProcSWVersion
                binding.btInfoPageVersion.text = newConnectionService.getPumpFirmwareVersions().btInfoPageVersion
                binding.bluetoothAddress.text = newConnectionService.getBluetoothAddress()
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