package com.example.heartr8

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.heartr8.ui.theme.HeartR8Theme
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.message.BasicNameValuePair


private const val REQUEST_ENABLE_BT = 1
private const val REQUEST_BLUETOOTH_PERMISSION = 2
internal const val REQUEST_BLUETOOTH_SCAN_PERMISSION = 3

class MainActivity : ComponentActivity() {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val leDeviceListAdapter: MutableList<BluetoothDevice> = mutableListOf()
    private var hasFound : Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter

        setUpBluetooth()
        findBLEDevices()



        super.onCreate(savedInstanceState)
        setContent {
            HeartR8Theme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }
    }



    fun setUpBluetooth(){
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show()
        }else{
            Toast.makeText(this, "Bluetooth is supported on this device", Toast.LENGTH_SHORT).show()
            // Check if Bluetooth permission is granted
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED) {
                // Bluetooth permission is granted
                if (bluetoothAdapter?.isEnabled == false) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                }
                Toast.makeText(this, "Bluetooth is enabled on this device", Toast.LENGTH_SHORT).show()
            } else {
                // Bluetooth permission is not granted, request permission
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.BLUETOOTH), REQUEST_BLUETOOTH_PERMISSION)
            }

        }
    }



    @SuppressLint("MissingPermission")
    fun findBLEDevices(){
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        val discoveredDevices = mutableListOf<BluetoothDevice>()
        var scanning = false

        if (!scanning) { // Stops scanning after a pre-defined scan period.
            scanning = watchForConnections(scanning)

            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
        }

    }
    fun watchForConnections(scanning : Boolean) : Boolean {

        val SCAN_PERIOD: Long = 5000
        val handler = Handler()
        var scanning = scanning
        handler.postDelayed({
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.BLUETOOTH_SCAN),REQUEST_BLUETOOTH_SCAN_PERMISSION)
            }
            val totalAmount = leDeviceListAdapter.size
            hasFound = false
            var name : String?
            for (i in 0 until totalAmount) {
                name = leDeviceListAdapter[i].name
                if (name == "HeartR8") {
                    Log.d(TAG, leDeviceListAdapter[i].name)
                    Log.d(TAG, leDeviceListAdapter[i].getAddress())
                    scanning = false
                    hasFound = true
                    val intent = Intent(this, DeviceControlActivity::class.java)
                    intent.putExtra("deviceAddress", "EE:20:15:74:B1:61")
                    startActivity(intent)
                    break
                }
            }
            if (!hasFound) {
                leDeviceListAdapter.clear()
                watchForConnections(scanning)
            }else {
                bluetoothLeScanner?.stopScan(leScanCallback)
            }




            /*
            for (device in discoveredDevices){
                println("Device Name: ${device.name}, Address: ${device.address}")
            }
            */
        }, SCAN_PERIOD)

        return scanning
    }

    // Device scan callback.
    private val leScanCallback: ScanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            leDeviceListAdapter.add(result.device)
        }
    }
}



@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column (verticalArrangement = Arrangement.Center){
        Text(
            text = "Looking for the HeartR8 Device",
            fontSize = 80.sp,
            lineHeight = 80.sp,
        )
        Text(
            text = "It will be just a second!",
            fontSize = 40.sp,
            lineHeight = 40.sp,
            textAlign = TextAlign.End
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HeartR8Theme {
        Greeting("Android")
    }
}