package app.aaps.pump.danars.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danars.R
import app.aaps.pump.danars.databinding.DanarsBlescannerActivityBinding
import app.aaps.pump.danars.events.EventDanaRSDeviceChange
import app.aaps.pump.danars.services.BleTransport
import app.aaps.pump.danars.services.ScannedDevice
import java.util.regex.Pattern
import javax.inject.Inject

class BLEScanActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var config: Config
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var context: Context
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var bleTransport: BleTransport

    private var listAdapter: ListAdapter? = null
    private val devices = ArrayList<ScannedDevice>()

    private lateinit var binding: DanarsBlescannerActivityBinding

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DanarsBlescannerActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        title = rh.gs(app.aaps.pump.dana.R.string.danars_pairing)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val isEmulating = config.emulateDanaRSv1() || config.emulateDanaRSv3() || config.emulateDanaBLE5()
        if (!isEmulating && !blePreCheck.prerequisitesCheck(this)) {
            ToastUtils.errorToast(this, getString(app.aaps.core.ui.R.string.need_connect_permission))
            finish()
        }

        listAdapter = ListAdapter()
        binding.bleScannerListview.emptyView = binding.bleScannerNoDevice
        binding.bleScannerListview.adapter = listAdapter
        listAdapter?.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        bleTransport.adapter.enable()
        startScan()
    }

    override fun onPause() {
        super.onPause()
        stopScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.bleScannerListview.adapter = null
    }

    private fun startScan() {
        bleTransport.scanner.startScan { device -> addBleDevice(device) }
    }

    private fun stopScan() {
        bleTransport.scanner.stopScan()
    }

    private fun addBleDevice(device: ScannedDevice) {
        if (device.name.isEmpty()) return
        if (!isSNCheck(device.name) || devices.contains(device)) return
        devices.add(device)
        runOnUiThread { listAdapter?.notifyDataSetChanged() }
    }

    internal inner class ListAdapter : BaseAdapter() {

        override fun getCount(): Int = devices.size
        override fun getItem(i: Int): ScannedDevice = devices[i]
        override fun getItemId(i: Int): Long = 0

        override fun getView(i: Int, convertView: View?, parent: ViewGroup?): View {
            var v = convertView
            val holder: ViewHolder
            if (v == null) {
                v = View.inflate(applicationContext, R.layout.danars_blescanner_item, null)
                holder = ViewHolder(v)
                v.tag = holder
            } else {
                holder = v.tag as ViewHolder
            }
            val item = getItem(i)
            holder.setData(item)
            return v!!
        }

        private inner class ViewHolder(v: View) : View.OnClickListener {

            private lateinit var item: ScannedDevice
            private val name: TextView = v.findViewById(R.id.ble_name)
            private val address: TextView = v.findViewById(R.id.ble_address)

            init {
                v.setOnClickListener(this@ViewHolder)
            }

            override fun onClick(v: View) {
                preferences.put(DanaStringKey.MacAddress, item.address)
                preferences.put(DanaStringKey.RsName, name.text.toString())
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bleTransport.adapter.createBond(item.address)
                    rxBus.send(EventDanaRSDeviceChange())
                } else {
                    ToastUtils.errorToast(context, context.getString(app.aaps.core.ui.R.string.need_connect_permission))
                }
                finish()
            }

            fun setData(data: ScannedDevice) {
                var tTitle = data.name
                if (tTitle.isEmpty()) {
                    tTitle = "(unknown)"
                } else if (tTitle.length > 10) {
                    tTitle = tTitle.substring(0, 10)
                }
                name.text = tTitle
                address.text = data.address
                item = data
            }
        }
    }

    @Suppress("RegExpSimplifiable")
    private fun isSNCheck(sn: String): Boolean {
        val regex = "^([a-zA-Z]{3})([0-9]{5})([a-zA-Z]{2})$"
        val p = Pattern.compile(regex)
        val m = p.matcher(sn)
        return m.matches()
    }
}
