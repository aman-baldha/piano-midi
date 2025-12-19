package com.midi.pianomidi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.UUID

/**
 * Manager for Superr Service BLE Characteristics
 * 
 * Service UUID: 12345678-1234-5678-1234-56789abc0000
 * Characteristics:
 * - Sensitivity (...0001): Byte (0-100), Default 50
 * - Theme (...0002): Byte (0=Aurora, 1=Fire, 2=Matrix)
 * - Transpose (...0003): Signed Byte (-12 to +12)
 */
class SuperrServiceManager private constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "SuperrServiceManager"
        
        // Service UUID
        private val SUPERR_SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abc0000")
        
        // Characteristic UUIDs
        private val SENSITIVITY_CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abc0001")
        private val THEME_CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abc0002")
        private val TRANSPOSE_CHAR_UUID = UUID.fromString("12345678-1234-5678-1234-56789abc0003")
        
        @Volatile
        private var INSTANCE: SuperrServiceManager? = null
        
        fun getInstance(context: Context): SuperrServiceManager {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                INSTANCE ?: SuperrServiceManager(appCtx).also { INSTANCE = it }
            }
        }
    }
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null
    
    /**
     * Connect to a Bluetooth device's GATT service
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        if (bluetoothGatt != null && connectedDevice?.address == device.address) {
            Log.d(TAG, "Already connected to device: ${device.address}")
            return
        }
        
        disconnect()
        
        connectedDevice = device
        Log.d(TAG, "Connecting to GATT service on device: ${device.address}")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bluetoothGatt = device.connectGatt(
                    context,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                @Suppress("DEPRECATION")
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to GATT: ${e.message}", e)
            bluetoothGatt = null
            connectedDevice = null
        }
    }
    
    /**
     * Disconnect from GATT service
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting GATT: ${e.message}", e)
            }
        }
        bluetoothGatt = null
        connectedDevice = null
    }
    
    /**
     * Set sensitivity (0-100)
     */
    @SuppressLint("MissingPermission")
    fun setSensitivity(value: Int) {
        val clampedValue = value.coerceIn(0, 100)
        writeCharacteristic(SENSITIVITY_CHAR_UUID, byteArrayOf(clampedValue.toByte()))
        Log.d(TAG, "Set sensitivity: $clampedValue")
    }
    
    /**
     * Set theme (0=Aurora, 1=Fire, 2=Matrix)
     */
    @SuppressLint("MissingPermission")
    fun setTheme(theme: Int) {
        val clampedTheme = theme.coerceIn(0, 2)
        writeCharacteristic(THEME_CHAR_UUID, byteArrayOf(clampedTheme.toByte()))
        Log.d(TAG, "Set theme: $clampedTheme")
    }
    
    /**
     * Set transpose (-12 to +12)
     */
    @SuppressLint("MissingPermission")
    fun setTranspose(value: Int) {
        val clampedValue = value.coerceIn(-12, 12)
        writeCharacteristic(TRANSPOSE_CHAR_UUID, byteArrayOf(clampedValue.toByte()))
        Log.d(TAG, "Set transpose: $clampedValue")
    }
    
    /**
     * Write to a characteristic
     */
    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(characteristicUuid: UUID, value: ByteArray) {
        val gatt = bluetoothGatt ?: run {
            Log.w(TAG, "GATT not connected, cannot write characteristic")
            return
        }
        
        try {
            val service = gatt.getService(SUPERR_SERVICE_UUID)
            if (service == null) {
                Log.w(TAG, "Superr Service not found")
                return
            }
            
            val characteristic = service.getCharacteristic(characteristicUuid)
            if (characteristic == null) {
                Log.w(TAG, "Characteristic not found: $characteristicUuid")
                return
            }
            
            // Set write type
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            
            // Set value
            if (!characteristic.setValue(value)) {
                Log.e(TAG, "Failed to set characteristic value")
                return
            }
            
            // Write characteristic
            if (!gatt.writeCharacteristic(characteristic)) {
                Log.e(TAG, "Failed to write characteristic")
            } else {
                Log.d(TAG, "Successfully wrote to characteristic: $characteristicUuid")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing characteristic: ${e.message}", e)
        }
    }
    
    /**
     * GATT Callback for connection and service discovery
     */
    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected, discovering services...")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected")
                    bluetoothGatt = null
                    connectedDevice = null
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SUPERR_SERVICE_UUID)
                if (service != null) {
                    Log.d(TAG, "Superr Service discovered successfully")
                    // Initialize default values
                    setSensitivity(50) // Default sensitivity
                } else {
                    Log.w(TAG, "Superr Service not found on device")
                }
            } else {
                Log.e(TAG, "Service discovery failed: $status")
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful: ${characteristic.uuid}")
            } else {
                Log.e(TAG, "Characteristic write failed: $status")
            }
        }
    }
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean {
        return bluetoothGatt != null && connectedDevice != null
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        disconnect()
    }
}

