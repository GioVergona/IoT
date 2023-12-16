package com.example.heartr8

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.nfc.NfcAdapter.EXTRA_DATA
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.heartr8.ui.theme.HeartR8Theme
import kotlinx.coroutines.flow.MutableStateFlow
import java.lang.Compiler.enable
import java.util.UUID

private val currentHeartRate = MutableStateFlow("0")
private const val TAG = "BluetoothLeService"
private val REQUEST_BLUETOOTH_CONNECT_PERMISSION = 1

class BluetoothLeService : Service() {

    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED


    private val bluetoothGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                connectionState = STATE_CONNECTED
                broadcastUpdate(ACTION_GATT_CONNECTED)
                bluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
                setCharacteristicNotification(characteristic, true)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
            } else {
                Log.i(TAG, "onServicesDiscovered received: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.let { gatt ->
            gatt.readCharacteristic(characteristic)
        } ?: run {
            Log.w(TAG, "BluetoothGatt not initialized")
            return
        }
    }



    fun getSupportedGattServices(): List<BluetoothGattService?>? {
        return bluetoothGatt?.services
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    fun initialize(): Boolean {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }
    fun connect(address: String): Boolean {
        bluetoothAdapter?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                // connect to the GATT server on the device
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {


                }
                bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
                return true
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Device not found with provided address.  Unable to connect.")
                return false
            }
        } ?: run {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return false
        }
    }
    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
        val intent = Intent(action)

        val flag = characteristic.properties
        val format = when (flag and 0x01) {
            0x01 -> {
                Log.d(TAG, "Heart rate format UINT16.")
                BluetoothGattCharacteristic.FORMAT_UINT16
            }
            else -> {
                Log.d(TAG, "Heart rate format UINT8.")
                BluetoothGattCharacteristic.FORMAT_UINT8
            }
        }
        val heartRate = characteristic.getIntValue(format, 1)
        Log.d(TAG, String.format("Received heart rate: %d", heartRate))
        intent.putExtra("HeartRate", (heartRate).toString())

        sendBroadcast(intent)
    }
    @SuppressLint("MissingPermission")
    fun setCharacteristicNotification(
        characteristic: BluetoothGattCharacteristic,
        enabled: Boolean
    ) {
        bluetoothGatt?.let { gatt ->
            gatt.setCharacteristicNotification(characteristic, enabled)
            // This is specific to Heart Rate Measurement.

            val descriptor = characteristic.getDescriptor(UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG))
            descriptor.value = if (enabled) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else byteArrayOf( 0x00, 0x00)
            gatt.writeDescriptor(descriptor)

        } ?: run {
            Log.w(TAG, "BluetoothGatt not initialized")
        }
    }


    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    inner class LocalBinder : Binder(), IBinder {
        fun getService() : BluetoothLeService {
            return this@BluetoothLeService
        }
    }
    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }

    private fun close() {
        bluetoothGatt?.let { gatt ->
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            gatt.close()
            bluetoothGatt = null
        }
    }

    companion object {
        const val ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE"

        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTED = 2

    }


}

class DeviceControlActivity : ComponentActivity() {

    private var bluetoothService : BluetoothLeService? = null
    //private var deviceAddress : String = intent.getStringExtra("deviceAddress").toString()
    private var deviceAddress : String = ""
    private var connected : Boolean = false

    // Code to manage Service lifecycle.
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            componentName: ComponentName,
            service: IBinder
        ) {
            bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothService?.let { bluetooth ->
                if (!bluetooth.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth")
                    finish()
                }
                // perform device connection
                connected = bluetooth.connect(deviceAddress)
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            bluetoothService = null
        }
    }

    val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your
                // app.
            } else {
                // Explain to the user that the feature is unavailable because the
                // feature requires a permission that the user has denied. At the
                // same time, respect the user's decision. Don't link to system
                // settings in an effort to convince the user to change their
                // decision.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        /*requestPermissions(this,
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
            REQUEST_BLUETOOTH_CONNECT_PERMISSION)*/
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_BLUETOOTH_CONNECT_PERMISSION
        )
        super.onCreate(savedInstanceState)
        deviceAddress =  intent.getStringExtra("deviceAddress").toString()
        //setContentView(R.layout.gatt_services_characteristics)
        setContent {
            HeartR8Theme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CurrentHeartRate("0")
                }
            }
        }


        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

    }

    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    connected = true
                    updateConnectionState(R.string.connected)
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    connected = false
                    updateConnectionState(R.string.disconnected)
                }
                BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED -> {
                    // Show all the supported services and characteristics on the user interface.
                    val haertRateCharacteristic = displayGattServices(bluetoothService?.getSupportedGattServices())

                }
                BluetoothLeService.ACTION_DATA_AVAILABLE -> {
                    // Show all the supported services and characteristics on the user interface.
                    Log.i(TAG, intent.getStringExtra("HeartRate").toString())
                    currentHeartRate.value = intent.getStringExtra("HeartRate").toString()
                }
            }
        }
    }

    private fun displayGattServices(supportedGattServices: List<BluetoothGattService?>?): BluetoothGattCharacteristic? {
        if (supportedGattServices == null) return null
        var uuid: String?
        val unknownServiceString: String = resources.getString(R.string.unknown_service)
        val unknownCharaString: String = resources.getString(R.string.unknown_characteristic)
        val gattServiceData: MutableList<HashMap<String, String>> = mutableListOf()
        val gattCharacteristicData: MutableList<ArrayList<HashMap<String, String>>> =
            mutableListOf()
        val mGattCharacteristics = mutableListOf<MutableList<BluetoothGattCharacteristic>>()

        supportedGattServices.forEach { gattService ->
            val currentServiceData = HashMap<String, String>()
            uuid = gattService?.uuid.toString()
            currentServiceData["LIST_NAME"] = SampleGattAttributes.lookup(uuid, unknownServiceString)
            currentServiceData["LIST_UUID"] = uuid!!
            gattServiceData += currentServiceData

            //val gattCharacteristicGroupData: ArrayList<HashMap<String, String>> = arrayListOf()
            val gattCharacteristics = gattService!!.characteristics
            //val charas: MutableList<BluetoothGattCharacteristic> = mutableListOf()

            // Loops through available Characteristics.
            gattCharacteristics.forEach { gattCharacteristic ->
                //charas += gattCharacteristic
                //val currentCharaData: HashMap<String, String> = hashMapOf()
                uuid = gattCharacteristic.uuid.toString()
                //currentCharaData["LIST_NAME"] = SampleGattAttributes.lookup(uuid, unknownCharaString)
                //currentCharaData["LIST_UUID"] = uuid!!
                if (uuid!!.startsWith("00002a37")) {
                    bluetoothService!!.readCharacteristic(gattCharacteristic)
                    return gattCharacteristic
                }
                //gattCharacteristicGroupData += currentCharaData
            }
            //mGattCharacteristics += charas
            //gattCharacteristicData += gattCharacteristicGroupData
        }
        return null
    }

    private fun updateConnectionState(connected: Int) {
        val connectionStatus = getString(connected)
        // Update the UI or perform actions based on the connection status
        // For example, you might update a TextView with the connection status.
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())
        if (bluetoothService != null) {
            val result = bluetoothService!!.connect(deviceAddress)
            Log.d(TAG, "Connect request result=$result")
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(gattUpdateReceiver)
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED)
            addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
        }
    }

}
object SampleGattAttributes {
    private val attributes: HashMap<Any?, Any?> = HashMap<Any?, Any?>()
    var HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb"
    var CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    var CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    init {
        // Sample Services.
        attributes["0000180d-0000-1000-8000-00805f9b34fb"] = "Heart Rate Service"
        // Sample Characteristics.
        attributes[HEART_RATE_MEASUREMENT] = "Heart Rate Measurement"
    }
    public fun lookup(uuid: String?, defaultName: String): String {
        val name = attributes.get(uuid);
        if (name == null) {
            return defaultName
        }
        return name.toString()
    }
}

@Composable
fun CurrentHeartRate(name: String, modifier: Modifier = Modifier) {
    val myCurrentHeartRate by currentHeartRate.collectAsState()
    Column (verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally){
        Text(
            text = "Current Heart Rate",
            fontSize = 30.sp,
            lineHeight = 30.sp
        )
        Text(
            text = myCurrentHeartRate,
            fontSize = 80.sp,
            lineHeight = 80.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HeartRatePreview() {
    HeartR8Theme {
        CurrentHeartRate("0")
    }
}