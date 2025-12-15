package com.midi.pianomidi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID

/**
 * Data class representing a MIDI device (either system MIDI or raw BLE device)
 */
data class MidiDeviceWrapper(
    val name: String,
    val type: DeviceType,
    val deviceInfo: MidiDeviceInfo? = null, // Null for an unconnected BLE device
    val bluetoothDevice: BluetoothDevice? = null, // Null for USB/Virtual devices
    val id: String // Unique ID for list tracking
)

enum class DeviceType { 
    USB, 
    BLUETOOTH, 
    VIRTUAL, 
    UNKNOWN 
}

/**
 * Data class representing a MIDI note event
 */
data class MidiNoteEvent(
    val note: Int,
    val velocity: Int,
    val isNoteOn: Boolean,
    val channel: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Callback interface for MIDI device discovery events
 */
interface MidiDeviceDiscoveryCallback {
    fun onDeviceFound(device: MidiDeviceWrapper)
    fun onDeviceRemoved(device: MidiDeviceWrapper)
    fun onScanComplete(devices: List<MidiDeviceWrapper>)
    fun onScanError(error: String)
}

/**
 * Callback interface for MIDI input events
 */
interface MidiInputCallback {
    fun onNoteOn(note: Int, velocity: Int, channel: Int)
    fun onNoteOff(note: Int, velocity: Int, channel: Int)
    fun onMidiMessage(message: ByteArray, timestamp: Long)
}

/**
 * Callback interface for connection state changes
 */
interface MidiConnectionCallback {
    fun onConnected(device: MidiDeviceWrapper)
    fun onDisconnected(device: MidiDeviceWrapper)
    fun onConnectionError(error: String)
    fun onReconnecting(device: MidiDeviceWrapper)
    fun onReconnected(device: MidiDeviceWrapper)
}

/**
 * Manager class for handling MIDI device connections, scanning, and input events.
 * Supports USB, Bluetooth MIDI (paired), and BLE MIDI (unpaired) devices.
 */
class MidiConnectionManager private constructor(
    private val context: Context,
    private val midiManager: MidiManager
) {
    companion object {
        private const val TAG = "MidiConnectionManager"
        // Standard MIDI Service UUID (BLE MIDI)
        private val MIDI_SERVICE_UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        
        @Volatile private var INSTANCE: MidiConnectionManager? = null
        
        fun getInstance(context: Context): MidiConnectionManager {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                val midiManager = appCtx.getSystemService(Context.MIDI_SERVICE) as MidiManager
                INSTANCE ?: MidiConnectionManager(appCtx, midiManager).also { INSTANCE = it }
            }
        }
        
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val discoveredDevices = mutableListOf<MidiDeviceWrapper>()
    private var isScanning = false
    
    // Callbacks
    private var discoveryCallback: MidiDeviceDiscoveryCallback? = null
    private var connectionCallback: MidiConnectionCallback? = null
    private var inputCallback: MidiInputCallback? = null
    
    // BLE Components
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner
    
    // Active Connection
    private var activeDevice: MidiDevice? = null
    private var activeWrapper: MidiDeviceWrapper? = null
    private val outputPorts = mutableListOf<android.media.midi.MidiOutputPort>()
    private var rgbController: MidiRgbController? = null
    
    // System MIDI Device Callback (USB / Paired BLE)
    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            handler.post {
                handleSystemDeviceFound(info)
            }
        }
        
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            handler.post {
                handleSystemDeviceRemoved(info)
            }
        }
    }
    
    // BLE Scan Callback (Unpaired BLE Devices)
    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: "Unknown MIDI"
            val deviceAddress = device.address
            
            // Avoid duplicates
            if (discoveredDevices.any { it.id == deviceAddress }) return
            
            val wrapper = MidiDeviceWrapper(
                name = deviceName,
                type = DeviceType.BLUETOOTH,
                bluetoothDevice = device,
                deviceInfo = null, // No MidiDeviceInfo yet!
                id = deviceAddress
            )
            
            Log.d(TAG, "BLE Device Found: $deviceName ($deviceAddress)")
            discoveredDevices.add(wrapper)
            handler.post { 
                discoveryCallback?.onDeviceFound(wrapper) 
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan Failed: $errorCode")
            handler.post { 
                discoveryCallback?.onScanError("BLE Scan Failed: $errorCode") 
            }
        }
    }
    
    init {
        midiManager.registerDeviceCallback(deviceCallback, handler)
    }
    
    fun setDiscoveryCallback(cb: MidiDeviceDiscoveryCallback) { 
        discoveryCallback = cb 
    }
    
    fun setConnectionCallback(cb: MidiConnectionCallback) { 
        connectionCallback = cb 
    }
    
    fun setInputCallback(cb: MidiInputCallback) { 
        inputCallback = cb 
    }
    
    /**
     * Scan for available MIDI devices (USB, paired Bluetooth, and unpaired BLE)
     */
    @SuppressLint("MissingPermission")
    fun scanForDevices() {
        if (isScanning) {
            Log.w(TAG, "Scan already in progress")
            return
        }
        
        isScanning = true
        discoveredDevices.clear()
        Log.d(TAG, "Starting Scan (System + BLE)...")
        
        // 1. Scan System Devices (USB / Already Paired)
        val systemDevices = midiManager.devices
        Log.d(TAG, "Found ${systemDevices.size} system MIDI device(s)")
        systemDevices.forEach { handleSystemDeviceFound(it) }
        
        // 2. Start BLE Scan for new devices
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
            if (!hasPermissions()) {
                Log.e(TAG, "Missing Bluetooth Permissions!")
                handler.post {
                    discoveryCallback?.onScanError("Missing Bluetooth Permissions")
                }
                isScanning = false
                return
            }
            
            try {
                val filters = listOf(
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(MIDI_SERVICE_UUID))
                        .build()
                )
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                
                bleScanner?.startScan(filters, settings, bleScanCallback)
                Log.d(TAG, "BLE scan started")
                
                // Stop scanning after 10 seconds
                handler.postDelayed({ stopScan() }, 10000)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting BLE scan: ${e.message}", e)
                handler.post {
                    discoveryCallback?.onScanError("BLE scan error: ${e.message}")
                }
                stopScan()
            }
        } else {
            Log.w(TAG, "Bluetooth not enabled")
            handler.postDelayed({ stopScan() }, 1000) // Finish quickly if no BLE
        }
    }
    
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        
        if (bluetoothAdapter?.isEnabled == true && hasPermissions()) {
            try {
                bleScanner?.stopScan(bleScanCallback)
                Log.d(TAG, "BLE scan stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping BLE scan: ${e.message}", e)
            }
        }
        
        Log.d(TAG, "Scan Complete. Found ${discoveredDevices.size} device(s).")
        handler.post {
            discoveryCallback?.onScanComplete(discoveredDevices.toList())
        }
    }
    
    /**
     * Connect to a MIDI device
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(wrapper: MidiDeviceWrapper) {
        stopScan() // Stop scanning when connecting
        
        Log.d(TAG, "Connecting to ${wrapper.name} (Type: ${wrapper.type})...")
        
        // Disconnect from current device if connected
        disconnect()
        
        // CASE 1: Raw BLE Device (Found via Scan)
        if (wrapper.bluetoothDevice != null && wrapper.deviceInfo == null) {
            Log.d(TAG, "Opening Raw Bluetooth Device via openBluetoothDevice()...")
            
            if (!hasPermissions()) {
                val errorMsg = "Missing Bluetooth permissions to connect"
                Log.e(TAG, errorMsg)
                handler.post {
                    connectionCallback?.onConnectionError(errorMsg)
                }
                return
            }
            
            try {
                midiManager.openBluetoothDevice(
                    wrapper.bluetoothDevice,
                    { device -> 
                        handler.post {
                            handleDeviceOpened(device, wrapper)
                        }
                    },
                    handler
                )
            } catch (e: Exception) {
                val errorMsg = "Error opening Bluetooth device: ${e.message}"
                Log.e(TAG, errorMsg, e)
                handler.post {
                    connectionCallback?.onConnectionError(errorMsg)
                }
            }
        }
        // CASE 2: System Device (USB or Paired)
        else if (wrapper.deviceInfo != null) {
            Log.d(TAG, "Opening System Device via openDevice()...")
            
            try {
                midiManager.openDevice(
                    wrapper.deviceInfo,
                    { device -> 
                        handler.post {
                            handleDeviceOpened(device, wrapper)
                        }
                    },
                    handler
                )
            } catch (e: Exception) {
                val errorMsg = "Error opening system device: ${e.message}"
                Log.e(TAG, errorMsg, e)
                handler.post {
                    connectionCallback?.onConnectionError(errorMsg)
                }
            }
        } else {
            val errorMsg = "Invalid device wrapper: no deviceInfo or bluetoothDevice"
            Log.e(TAG, errorMsg)
            handler.post {
                connectionCallback?.onConnectionError(errorMsg)
            }
        }
    }
    
    /**
     * Handle device opened successfully
     */
    private fun handleDeviceOpened(device: MidiDevice?, wrapper: MidiDeviceWrapper) {
        if (device == null) {
            Log.e(TAG, "Failed to open device!")
            connectionCallback?.onConnectionError("Failed to open device")
            return
        }
        
        try {
            activeDevice = device
            activeWrapper = wrapper
            
            Log.d(TAG, "Device Opened Successfully: ${wrapper.name}")
            Log.d(TAG, "Device info - Input Ports: ${device.info.inputPortCount}, " +
                    "Output Ports: ${device.info.outputPortCount}")
            
            // Setup MIDI input to receive data from device
            setupMidiInput(device)
            
            // Initialize RGB controller for lighting control
            try {
                rgbController = MidiRgbController(device)
                Log.d(TAG, "RGB controller initialized")
            } catch (e: Exception) {
                Log.w(TAG, "Could not initialize RGB controller: ${e.message}")
            }
            
            connectionCallback?.onConnected(wrapper)
            Log.d(TAG, "Successfully connected to: ${wrapper.name} (${wrapper.type})")
            
        } catch (e: Exception) {
            val errorMsg = "Error setting up device: ${e.message}"
            Log.e(TAG, errorMsg, e)
            connectionCallback?.onConnectionError(errorMsg)
            // Clean up on error
            try {
                device.close()
            } catch (closeException: Exception) {
                Log.e(TAG, "Error closing device after setup failure", closeException)
            }
            activeDevice = null
            activeWrapper = null
        }
    }
    
    /**
     * Setup MIDI input receiver to listen for MIDI events
     */
    private fun setupMidiInput(device: MidiDevice) {
        try {
            val outputPortCount = device.info.outputPortCount
            Log.d(TAG, "Device has $outputPortCount output port(s)")
            
            if (outputPortCount > 0) {
                // Create our receiver to handle incoming MIDI messages
                val receiver = object : MidiReceiver() {
                    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                        try {
                            // Extract the actual message bytes
                            val message = ByteArray(count)
                            System.arraycopy(msg, offset, message, 0, count)
                            
                            // Process MIDI message
                            processMidiMessage(message, timestamp)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing MIDI message", e)
                        }
                    }
                }
                
                // Open all available output ports and connect our receiver
                for (portIndex in 0 until outputPortCount) {
                    try {
                        val outputPort = device.openOutputPort(portIndex)
                        if (outputPort != null) {
                            outputPorts.add(outputPort)
                            
                            // Connect the output port to our receiver using reflection
                            try {
                                val connectMethod = outputPort.javaClass.getMethod("connect", MidiReceiver::class.java)
                                connectMethod.invoke(outputPort, receiver)
                                Log.d(TAG, "Successfully connected receiver to output port $portIndex")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error connecting receiver to output port $portIndex: ${e.message}", e)
                            }
                        } else {
                            Log.w(TAG, "Failed to open output port $portIndex (returned null)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening output port $portIndex: ${e.message}", e)
                    }
                }
                
                if (outputPorts.isEmpty()) {
                    Log.w(TAG, "No output ports could be opened")
                    connectionCallback?.onConnectionError("Device has no accessible output ports")
                } else {
                    Log.d(TAG, "Successfully opened ${outputPorts.size} output port(s)")
                }
            } else {
                Log.w(TAG, "Device has no output ports")
                connectionCallback?.onConnectionError("Device has no output ports")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MIDI input", e)
            connectionCallback?.onConnectionError("Failed to setup MIDI input: ${e.message}")
        }
    }
    
    /**
     * Process incoming MIDI messages and trigger appropriate callbacks
     */
    private fun processMidiMessage(message: ByteArray, timestamp: Long) {
        if (message.isEmpty()) return
        
        val status = message[0].toInt() and 0xFF
        val command = status and 0xF0
        val channel = status and 0x0F
        
        // Notify raw message callback
        handler.post {
            inputCallback?.onMidiMessage(message, timestamp)
        }
        
        when (command) {
            0x90 -> { // Note On (or Note Off with velocity 0)
                if (message.size >= 3) {
                    val note = message[1].toInt() and 0xFF
                    val velocity = message[2].toInt() and 0xFF
                    
                    handler.post {
                        if (velocity > 0) {
                            inputCallback?.onNoteOn(note, velocity, channel)
                        } else {
                            // Note On with velocity 0 is treated as Note Off
                            inputCallback?.onNoteOff(note, 0, channel)
                        }
                    }
                }
            }
            0x80 -> { // Note Off
                if (message.size >= 3) {
                    val note = message[1].toInt() and 0xFF
                    val velocity = message[2].toInt() and 0xFF
                    
                    handler.post {
                        inputCallback?.onNoteOff(note, velocity, channel)
                    }
                }
            }
        }
    }
    
    /**
     * Handle system device found (USB or paired Bluetooth)
     */
    private fun handleSystemDeviceFound(info: MidiDeviceInfo) {
        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown Device"
        val id = info.properties.getString(MidiDeviceInfo.PROPERTY_SERIAL_NUMBER) 
            ?: "system_${info.id}"
        
        if (discoveredDevices.any { it.id == id }) return
        
        val type = when (info.type) {
            MidiDeviceInfo.TYPE_USB -> DeviceType.USB
            MidiDeviceInfo.TYPE_BLUETOOTH -> DeviceType.BLUETOOTH
            MidiDeviceInfo.TYPE_VIRTUAL -> DeviceType.VIRTUAL
            else -> DeviceType.UNKNOWN
        }
        
        val wrapper = MidiDeviceWrapper(
            name = name,
            type = type,
            deviceInfo = info,
            bluetoothDevice = null,
            id = id
        )
        
        Log.d(TAG, "System Device Found: $name (Type: $type, " +
                "Input Ports: ${info.inputPortCount}, Output Ports: ${info.outputPortCount})")
        
        discoveredDevices.add(wrapper)
        handler.post {
            discoveryCallback?.onDeviceFound(wrapper)
        }
    }
    
    /**
     * Handle system device removed
     */
    private fun handleSystemDeviceRemoved(info: MidiDeviceInfo) {
        val id = info.properties.getString(MidiDeviceInfo.PROPERTY_SERIAL_NUMBER) 
            ?: "system_${info.id}"
        
        val removed = discoveredDevices.removeAll { it.id == id }
        if (removed) {
            val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown Device"
            Log.d(TAG, "System Device Removed: $name")
            
            // If the removed device is the currently connected one, handle disconnection
            if (activeWrapper?.id == id) {
                disconnect()
            }
            
            handler.post {
                val wrapper = MidiDeviceWrapper(
                    name = name,
                    type = DeviceType.UNKNOWN,
                    deviceInfo = info,
                    id = id
                )
                discoveryCallback?.onDeviceRemoved(wrapper)
            }
        }
    }
    
    /**
     * Disconnect from the current MIDI device
     */
    fun disconnect() {
        activeDevice?.let { device ->
            try {
                // Close all MIDI output ports
                outputPorts.forEach { port ->
                    try {
                        port.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing output port", e)
                    }
                }
                outputPorts.clear()
                
                // Release RGB controller
                rgbController?.release()
                rgbController = null
                
                device.close()
                
                val wrapper = activeWrapper
                activeDevice = null
                activeWrapper = null
                
                wrapper?.let {
                    connectionCallback?.onDisconnected(it)
                    Log.d(TAG, "Disconnected from: ${it.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting device", e)
            }
        }
    }
    
    /**
     * Get the list of discovered devices
     */
    fun getDiscoveredDevices(): List<MidiDeviceWrapper> {
        return discoveredDevices.toList()
    }
    
    /**
     * Get the currently connected device
     */
    fun getCurrentDevice(): MidiDeviceWrapper? {
        return activeWrapper
    }
    
    /**
     * Check if currently connected to a device
     */
    fun isConnected(): Boolean {
        return activeDevice != null
    }
    
    /**
     * Attempt to reconnect to the last connected device
     */
    fun reconnect() {
        activeWrapper?.let { wrapper ->
            Log.d(TAG, "Attempting to reconnect to: ${wrapper.name}")
            connectionCallback?.onReconnecting(wrapper)
            
            // Find the device in the current list
            val updatedDevice = discoveredDevices.find { it.id == wrapper.id }
            
            if (updatedDevice != null) {
                connectToDevice(updatedDevice)
                handler.postDelayed({
                    if (isConnected()) {
                        connectionCallback?.onReconnected(updatedDevice)
                    }
                }, 500)
            } else {
                val errorMsg = "Device not found for reconnection: ${wrapper.name}"
                Log.e(TAG, errorMsg)
                connectionCallback?.onConnectionError(errorMsg)
            }
        } ?: run {
            Log.w(TAG, "No previous device to reconnect to")
        }
    }
    
    /**
     * Get RGB controller for lighting control
     */
    fun getRgbController(): MidiRgbController? {
        return rgbController
    }
    
    /**
     * Check if required permissions are granted
     */
    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Clean up resources and unregister callbacks
     */
    fun cleanup() {
        disconnect()
        rgbController?.release()
        rgbController = null
        stopScan()
        midiManager.unregisterDeviceCallback(deviceCallback)
        discoveryCallback = null
        inputCallback = null
        connectionCallback = null
        discoveredDevices.clear()
        Log.d(TAG, "MidiConnectionManager cleaned up")
    }
}
