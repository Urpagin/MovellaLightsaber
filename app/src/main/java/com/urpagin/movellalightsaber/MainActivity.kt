package com.urpagin.movellalightsaber

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.system.exitProcess

const val DEVICE_ADDRESS = "D4:22:CD:00:50:50"
val MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("15172001-4947-11e9-8646-d663bd873d93")
val SHORT_PAYLOAD_CHARACTERISTIC_UUID: UUID =
    UUID.fromString("15172004-4947-11e9-8646-d663bd873d93")

class MainActivity : AppCompatActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null
    private lateinit var bluetoothDataTextView: TextView

    private fun is_bluetooth_ready(): Boolean {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter ?: return false

        if (!bluetoothAdapter.isEnabled) {
            return false
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!is_bluetooth_ready()) {
            moveTaskToBack(true)
            exitProcess(-1)
        }
        setContentView(R.layout.activity_main)

        bluetoothDataTextView = findViewById(R.id.bluetoothDataTextView)
        appendToTextView("onCreate")
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val startScanButton: Button = findViewById(R.id.startScanButton)
        startScanButton.setOnClickListener {
            if (!hasAllRequiredPermissions()) {
                appendToTextView("Requesting necessary permissions...")
                requestPermissions()
            } else {
                appendToTextView("Starting BLE scan...")
                startBLEScan()
            }
        }



    }

    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            REQUEST_ALL_PERMISSIONS
        )
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_ALL_PERMISSIONS -> {
                if (hasAllRequiredPermissions()) {
                    appendToTextView("Permissions granted, starting BLE scan...")
                    startBLEScan()
                } else {
                    appendToTextView("Permissions denied")
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    private fun hasAllRequiredPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }


    companion object {
        private const val REQUEST_ALL_PERMISSIONS = 1
        // Other constants...
    }


    //@SuppressLint("MissingPermission")
    private fun startBLEScan() {
        appendToTextView("startBLEScan in")

        // Configure BLE scan settings
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Start BLE scan with defined settings and callback
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bluetoothAdapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        appendToTextView("BLE scan started...")
    }


    private val scanCallback = object : ScanCallback() {
        //@SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceAddress = result.device.address
            val deviceName = result.device.name ?: "Unknown"
            appendToTextView("Found device: $deviceName, Address: $deviceAddress")

            if (deviceAddress == DEVICE_ADDRESS) {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(this)
                appendToTextView("Target device found: $deviceAddress")
                connectToDevice(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            appendToTextView("BLE scan failed with error code: $errorCode")
        }
    }



    private fun connectToDevice(device: BluetoothDevice) {
        appendToTextView("connectToDevice")
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service =
                    gatt.getService(UUID.fromString(SHORT_PAYLOAD_CHARACTERISTIC_UUID.toString()))
                val characteristic =
                    service.getCharacteristic(UUID.fromString(SHORT_PAYLOAD_CHARACTERISTIC_UUID.toString()))
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor =
                    characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)

                val measurementCharacteristic =
                    service.getCharacteristic(UUID.fromString(MEASUREMENT_CHARACTERISTIC_UUID.toString()))
                val binaryMessage = byteArrayOf(0x01, 0x01, 0x06)
                measurementCharacteristic.setValue(binaryMessage)
                gatt.writeCharacteristic(measurementCharacteristic)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
            ) {
                val data = characteristic.value
                val formattedData = encodeFreeAcceleration(data)
                runOnUiThread {
                    bluetoothDataTextView.text = formattedData
                }
            }
        })
    }

    private fun encodeFreeAcceleration(bytes: ByteArray): String {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val timestamp = buffer.int
        val x = buffer.float
        val y = buffer.float
        val z = buffer.float
        return "Timestamp: $timestamp, X: $x, Y: $y, Z: $z"
    }

    private fun appendToTextView(text: String) {
        runOnUiThread {
            bluetoothDataTextView.append("$text\n")
            // Scroll to the bottom
            val scrollView: ScrollView = findViewById(R.id.scrollView)
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }


}
