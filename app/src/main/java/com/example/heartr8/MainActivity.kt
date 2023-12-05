package com.example.heartr8

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
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

private const val REQUEST_ENABLE_BT = 1
private const val REQUEST_BLUETOOTH_PERMISSION = 2
private const val REQUEST_BLUETOOTH_SCAN_PERMISSION = 3

class MainActivity : ComponentActivity() {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null


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

    fun findBLEDevices(){
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        val discoveredDevices = mutableListOf<BluetoothDevice>()
        var scanning = false
        val handler = Handler()
        val SCAN_PERIOD: Long = 10000
        val leScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (!discoveredDevices.contains(device)) {
                    discoveredDevices.add(device)
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                // Handle a batch of scan results here
            }

            override fun onScanFailed(errorCode: Int) {
                // Handle scan failure here
            }
        }

        if (!scanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                scanning = false
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.BLUETOOTH_SCAN),REQUEST_BLUETOOTH_SCAN_PERMISSION)
                }
                bluetoothLeScanner?.stopScan(leScanCallback)
                for (device in discoveredDevices){
                    println("Device Name: ${device.name}, Address: ${device.address}")
                }
            }, SCAN_PERIOD)
            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
        }

    }
}



@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    HeartR8Theme {
        Greeting("Android")
    }
}