package com.midi.pianomidi

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Helper class for Bluetooth operations and checks
 */
object BluetoothHelper {
    private const val TAG = "BluetoothHelper"
    
    /**
     * Check if Bluetooth is available on this device
     */
    fun isBluetoothAvailable(context: Context): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth availability: ${e.message}")
            false
        }
    }
    
    /**
     * Check if Bluetooth is currently enabled
     */
    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter
            adapter?.isEnabled == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth state: ${e.message}")
            false
        }
    }
    
    /**
     * Get Bluetooth adapter
     */
    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        return try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Bluetooth adapter: ${e.message}")
            null
        }
    }
    
    /**
     * Request to enable Bluetooth (returns intent for Activity result)
     */
    fun getEnableBluetoothIntent(): Intent? {
        return try {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Bluetooth enable intent: ${e.message}")
            null
        }
    }
}

