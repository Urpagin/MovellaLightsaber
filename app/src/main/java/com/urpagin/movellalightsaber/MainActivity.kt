package com.urpagin.movellalightsaber

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random
import kotlin.system.exitProcess

const val DEVICE_ADDRESS = "D4:22:CD:00:50:50"
val MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("15172001-4947-11e9-8646-d663bd873d93")
val SHORT_PAYLOAD_CHARACTERISTIC_UUID: UUID = UUID.fromString("15172004-4947-11e9-8646-d663bd873d93")


class MainActivity : AppCompatActivity() {
    private lateinit var mediaPlayer: MediaPlayer
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

    private var pitch = 1.0f // Starting pitch

    fun playSoundWithPitchChange(resourceId: Int) {
        val mediaPlayer = MediaPlayer.create(this, resourceId)
        val start = -0.3
        val end = 0.3
        val randomValue: Float = (start + Random.nextFloat() * (end - start)).toFloat() // Generates a random double between 1.0 and 10.0

        pitch += randomValue
        val playbackParams = PlaybackParams()
        playbackParams.pitch = pitch
        mediaPlayer.playbackParams = playbackParams
        mediaPlayer.start()

        pitch = 1.0f

        mediaPlayer.setOnCompletionListener {
            mediaPlayer.release()
        }

        // Adjust the pitch for the next playback
         // Adjust this value as needed
    }



    private fun unpairDevice(device: BluetoothDevice) {
        try {
            // Use an empty array to indicate no parameters for the method
            val method = device.javaClass.getMethod("removeBond", *arrayOf<Class<*>>())
            method.invoke(device)
            runOnUiThread { appendToTextView("Successfully initiated unpairing for ${device.address}") }
        } catch (e: Exception) {
            Log.e("unpairDevice", "Exception when trying to unpair ${device.address}", e)
            runOnUiThread { appendToTextView("[[unpairDevice]] - Exception when trying to unpair ${device.address} - ${e.message}") }
        }
    }



    private fun disconnectFromDevice() {
        bluetoothGatt?.device?.let { device ->
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // Request necessary permissions or handle the lack of them
                return
            }

            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            runOnUiThread { appendToTextView("Disconnected from device") }

            // Attempt to unpair the device
            unpairDevice(device)
            runOnUiThread { appendToTextView("Unpaired from device") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!is_bluetooth_ready()) {
            moveTaskToBack(true)
            exitProcess(-1)
        }



        // acccess bg location
        // Initialize MediaPlayer and set the audio resource
        mediaPlayer = MediaPlayer.create(this, R.raw.lightsaber)
        // Starts the audio
        mediaPlayer.start()
        // Waits for the audio to complete, then cleans
        //mediaPlayerInit.setOnCompletionListener {
        //    mediaPlayerInit.release()
        //}



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(baseContext,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    PERMISSION_CODE)
            }
        }


        // ----

        setContentView(R.layout.activity_main)

        bluetoothDataTextView = findViewById(R.id.bluetoothDataTextView)

        // Assuming this code is within an Activity or Fragment where the EditText is located

        // Step 1: Access the EditText
        val soundTriggerThreshold = findViewById<EditText>(R.id.triggerThresholdNumber)

        // Step 2: Extract the Text
        val textValue = soundTriggerThreshold.text.toString()

        // Step 3: Convert to Integer
        val intValue = textValue.toIntOrNull()  // Safe conversion to Int, returns null if conversion fails

        // Optional: Check if the conversion was successful
        if (intValue != null) {
            // Use the integer value as needed
            Log.d("YourActivity", "The integer value is: $intValue")
        } else {
            // Handle the case where the text is not a valid integer
            Log.d("YourActivity", "The entered text is not a valid integer")
        }



        val disconnectButton: Button = findViewById(R.id.disconnectButton)
        disconnectButton.setOnClickListener {
            disconnectFromDevice()
        }

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
        private const val PERMISSION_CODE = 5
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

        connectToKnownDevice()
        //bluetoothAdapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        //bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)
        appendToTextView("BLE scan started...")



    }


    //private val scanCallback = object : ScanCallback() {
    //    //@SuppressLint("MissingPermission")
    //    override fun onScanResult(callbackType: Int, result: ScanResult) {
    //        val deviceAddress = result.device.address
    //        val deviceName = result.device.name ?: "Unknown"
    //        appendToTextView("Found device: $deviceName, Address: $deviceAddress")
//
    //        if (deviceAddress == DEVICE_ADDRESS) {
    //            bluetoothAdapter.bluetoothLeScanner?.stopScan(this)
    //            appendToTextView("Target device found: $deviceAddress")
    //            connectToDevice(result.device)
    //        }
    //    }
//
    //    override fun onScanFailed(errorCode: Int) {
    //        appendToTextView("BLE scan failed with error code: $errorCode")
    //    }
    //}

    private fun connectToKnownDevice() {
        val deviceAddress = "D4:22:CD:00:50:50"
        val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(deviceAddress)
        if (device == null) {
            appendToTextView("Device not found.")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider requesting the missing permission.
            return
        }

        bluetoothGatt = device.connectGatt(this, true, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            runOnUiThread { appendToTextView("[[onConnectionStateChange]]") }

            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { appendToTextView("Connected to GATT server.") }
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread { appendToTextView("Disconnected from GATT server.") }
                runOnUiThread { appendToTextView("\n\n--ATTEMPT TO RECONNECT--\n\n") }
                connectToKnownDevice() // Attempt reconnection
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            runOnUiThread { appendToTextView("[[onServicesDiscovered]]") }


            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (service in gatt.services) {
                    runOnUiThread { appendToTextView("Service: ${service.uuid}") }
                    for (characteristic in service.characteristics) {
                        runOnUiThread { appendToTextView("-- Characteristic: ${characteristic.uuid}") }
                    }
                }
                findCharacteristicAndEnableNotification(gatt)
            }
        }



        private fun findCharacteristicAndEnableNotification(gatt: BluetoothGatt) {
            runOnUiThread { appendToTextView("[[findCharacteristicAndEnableNotification]]") }

            val serviceUuid = UUID.fromString("15172000-4947-11e9-8646-d663bd873d93")
            val characteristicUuid = UUID.fromString("15172004-4947-11e9-8646-d663bd873d93")

            val desiredService = gatt.getService(serviceUuid)
            if (desiredService == null) {
                runOnUiThread { appendToTextView("Desired service is NULL!") }
                return
            }

            val desiredCharacteristic = desiredService.getCharacteristic(characteristicUuid)
            if (desiredCharacteristic != null) {
                enableCharacteristicNotification(gatt, desiredCharacteristic)
            } else {
                runOnUiThread { appendToTextView("Desired characteristic is NULL!") }
            }

        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == SHORT_PAYLOAD_CHARACTERISTIC_UUID) {
                val sensorData: SensorData = encodeFreeAcceleration(value)
                val soundThreshold: Int = getIntSoundTriggerThreshold(R.id.triggerThresholdNumber)
                if (abs(sensorData.x) > soundThreshold || abs(sensorData.y) > soundThreshold || abs(sensorData.z) > soundThreshold) {
                    // Start playing the audio
                    //playSoundWithPitchChange(R.raw.vsauce)
                    mediaPlayer.start()
                }
            }
        }



        private fun getIntSoundTriggerThreshold(editTextId: Int): Int {
            val editText = findViewById<EditText>(editTextId)
            val textValue = editText.text.toString()
            return textValue.toInt()
        }





        private fun enableCharacteristicNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            runOnUiThread { appendToTextView("[[enableCharacteristicNotification]]") }

            if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread { appendToTextView("Returned!") }
                return // Handle the permission request
            }

            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                runOnUiThread { appendToTextView("Setting the characteristic notification was NOT SUCCESSFUL!") }
            }
            runOnUiThread { appendToTextView("Setting the characteristic notification was successful") }

            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (descriptor == null) {
                runOnUiThread { appendToTextView("Descriptor is NULL!") }
            }

            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)

            runOnUiThread { appendToTextView("[[enableCharacteristicNotification - END]]") }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread { appendToTextView("Descriptor write successful") }

                val measurementCharacteristic = gatt.getService(UUID.fromString("15172000-4947-11e9-8646-d663bd873d93"))
                    ?.getCharacteristic(UUID.fromString("15172001-4947-11e9-8646-d663bd873d93"))

                if (measurementCharacteristic != null) {
                    val command: ByteArray = byteArrayOf(0x01, 0x01, 0x06)
                    measurementCharacteristic.value = command
                    if (gatt.writeCharacteristic(measurementCharacteristic)) {
                        runOnUiThread { appendToTextView("Write command sent") }
                    } else {
                        runOnUiThread { appendToTextView("Failed to send write command") }
                    }
                } else {
                    runOnUiThread { appendToTextView("Measurement characteristic not found") }
                }
            } else {
                runOnUiThread { appendToTextView("Descriptor write failed: $status") }
            }
        }










        // Implement other callback methods like onCharacteristicRead, onCharacteristicWrite, etc.
    }

    data class SensorData(
        val timestamp: Int,
        val x: Float,
        val y: Float,
        val z: Float
    )

    private fun encodeFreeAcceleration(bytes: ByteArray): SensorData {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sensorData = SensorData(
            timestamp = buffer.int,
            x = buffer.float,
            y = buffer.float,
            z = buffer.float
        )

        // Skip zero_padding (4 bytes)
        buffer.position(buffer.position() + 4)
        return sensorData
    }






    //private fun connectToDevice(device: BluetoothDevice) {
    //    appendToTextView("connectToDevice")
    //    if (ActivityCompat.checkSelfPermission(
    //            this, Manifest.permission.BLUETOOTH_CONNECT
    //        ) != PackageManager.PERMISSION_GRANTED
    //    ) {
    //        // TODO: Consider calling
    //        //    ActivityCompat#requestPermissions
    //        // here to request the missing permissions, and then overriding
    //        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
    //        //                                          int[] grantResults)
    //        // to handle the case where the user grants the permission. See the documentation
    //        // for ActivityCompat#requestPermissions for more details.
    //        return
    //    }
    //
    //
    //    bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
    //        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
    //            if (newState == BluetoothProfile.STATE_CONNECTED) {
    //                if (ActivityCompat.checkSelfPermission(
    //                        this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT
    //                    ) != PackageManager.PERMISSION_GRANTED
    //                ) {
    //                    // TODO: Consider calling
    //                    //    ActivityCompat#requestPermissions
    //                    // here to request the missing permissions, and then overriding
    //                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
    //                    //                                          int[] grantResults)
    //                    // to handle the case where the user grants the permission. See the documentation
    //                    // for ActivityCompat#requestPermissions for more details.
    //                    return
    //                }
    //                gatt.discoverServices()
    //            }
    //        }
//
    //        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
    //            val service =
    //                gatt.getService(UUID.fromString(SHORT_PAYLOAD_CHARACTERISTIC_UUID.toString()))
    //            val characteristic =
    //                service.getCharacteristic(UUID.fromString(SHORT_PAYLOAD_CHARACTERISTIC_UUID.toString()))
    //            if (ActivityCompat.checkSelfPermission(
    //                    this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT
    //                ) != PackageManager.PERMISSION_GRANTED
    //            ) {
    //                // TODO: Consider calling
    //                //    ActivityCompat#requestPermissions
    //                // here to request the missing permissions, and then overriding
    //                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
    //                //                                          int[] grantResults)
    //                // to handle the case where the user grants the permission. See the documentation
    //                // for ActivityCompat#requestPermissions for more details.
    //                return
    //            }
    //            gatt.setCharacteristicNotification(characteristic, true)
    //            val descriptor =
    //                characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
    //            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    //            gatt.writeDescriptor(descriptor)
//
    //            val measurementCharacteristic =
    //                service.getCharacteristic(UUID.fromString(MEASUREMENT_CHARACTERISTIC_UUID.toString()))
    //            val binaryMessage = byteArrayOf(0x01, 0x01, 0x06)
    //            measurementCharacteristic.setValue(binaryMessage)
    //            gatt.writeCharacteristic(measurementCharacteristic)
    //        }
//
    //        override fun onCharacteristicChanged(
    //            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
    //        ) {
    //            val data = characteristic.value
    //            val formattedData = encodeFreeAcceleration(data)
    //            runOnUiThread {
    //                bluetoothDataTextView.text = formattedData
    //            }
    //        }
    //    })
    //}

    //private fun encodeFreeAcceleration(bytes: ByteArray): String {
    //    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    //    val timestamp = buffer.int
    //    val x = buffer.float
    //    val y = buffer.float
    //    val z = buffer.float
    //    return "Timestamp: $timestamp, X: $x, Y: $y, Z: $z"
    //}

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
