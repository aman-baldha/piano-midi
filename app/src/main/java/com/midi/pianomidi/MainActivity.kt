package com.midi.pianomidi

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.midi.MidiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.midi.pianomidi.ui.BluetoothEnableDialog
import com.midi.pianomidi.ui.DeviceSelectionDialog
import com.midi.pianomidi.ui.PianoLearningScreen
import com.midi.pianomidi.ui.KeyHighlightColor
import com.midi.pianomidi.ui.theme.PianomidiTheme

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ENABLE_BLUETOOTH = 1
    }
    
    private var midiConnectionManager: MidiConnectionManager? = null
    private var learningModeController: LearningModeController? = null
    
    // Activity result launcher for enabling Bluetooth
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Bluetooth enabled by user")
            // Bluetooth is now enabled, proceed with MIDI device scan
            midiConnectionManager?.scanForDevices()
        } else {
            Log.d(TAG, "User cancelled Bluetooth enable")
        }
    }
    
    // Permission launcher for Bluetooth permissions (Android 12+)
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Log.d(TAG, "Bluetooth permissions granted")
            // Check if Bluetooth is enabled, if not request to enable it
            if (BluetoothHelper.isBluetoothEnabled(this)) {
                // Permissions granted and Bluetooth enabled, proceed with scan
                midiConnectionManager?.scanForDevices()
            } else {
                // Permissions granted but Bluetooth not enabled, request enable
                val intent = BluetoothHelper.getEnableBluetoothIntent()
                if (intent != null) {
                    bluetoothEnableLauncher.launch(intent)
                }
            }
        } else {
            Log.w(TAG, "Bluetooth permissions denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize MIDI Manager
        val midiManager = getSystemService(Context.MIDI_SERVICE) as? MidiManager
        if (midiManager == null) {
            Log.e(TAG, "MIDI service not available")
            finish()
            return
        }
        
        // Initialize MidiConnectionManager
        midiConnectionManager = MidiConnectionManager.getInstance(this)
        
        setContent {
            PianomidiTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PianoLearningScreenContent(
                        midiConnectionManager = midiConnectionManager!!,
                        onControllerCreated = { controller ->
                            this.learningModeController = controller
                        },
                        onRequestBluetoothEnable = {
                            requestBluetoothEnable()
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // Resume learning mode if it was paused
        learningModeController?.resume()
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        // Pause learning mode when activity is paused
        learningModeController?.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        // Clean up resources
        learningModeController?.cleanup()
        learningModeController = null
        // Note: Don't cleanup MidiConnectionManager here as it's a singleton
        // It will be cleaned up when the app is terminated
    }
    
    /**
     * Request user to enable Bluetooth
     */
    private fun requestBluetoothEnable() {
        // First check and request permissions if needed (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN
            )
            
            // Check if permissions are already granted
            val needsPermission = permissions.any {
                checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            
            if (needsPermission) {
                Log.d(TAG, "Requesting Bluetooth permissions")
                bluetoothPermissionLauncher.launch(permissions)
                return
            }
        }
        
        // Request to enable Bluetooth
        val intent = BluetoothHelper.getEnableBluetoothIntent()
        if (intent != null) {
            bluetoothEnableLauncher.launch(intent)
        } else {
            Log.e(TAG, "Could not create Bluetooth enable intent")
        }
    }
}

@Composable
fun PianoLearningScreenContent(
    midiConnectionManager: MidiConnectionManager,
    onControllerCreated: (LearningModeController) -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val TAG = "PianoLearningScreen"
    // State management for the UI
    var isConnected by remember { mutableStateOf(false) }
    var isVirtualMode by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentNote by remember { mutableStateOf<Note?>(null) }
    var previousNote by remember { mutableStateOf<Note?>(null) }
    var completedNotes by remember { mutableStateOf(0) }
    var totalNotes by remember { mutableStateOf(0) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var availableDevices by remember { mutableStateOf<List<MidiDeviceWrapper>>(emptyList()) }
    var highlightedNotes by remember { mutableStateOf<Map<Int, KeyHighlightColor>>(emptyMap()) }
    
    // Initialize song and learning controller
    val song = remember { TwinkleTwinkleLittleStar.createSong() }
    totalNotes = song.notes.size
    
    // Initialize piano sound player
    val soundPlayer = remember {
        PianoSoundPlayer(context)
    }
    
    // Cleanup sound player on dispose
    DisposableEffect(Unit) {
        onDispose {
            soundPlayer.release()
        }
    }
    
    // Initialize LearningModeController
    val learningController = remember {
        LearningModeController(
            song = song,
            midiConnectionManager = midiConnectionManager,
            callback = object : LearningModeCallback {
                override fun onNoteChanged(note: Note?, nextNote: Note?) {
                    // Turn off previous note's RGB light
                    if (isConnected && previousNote != null) {
                        val rgbController = midiConnectionManager.getRgbController()
                        rgbController?.turnOffKey(previousNote!!.midiNote)
                    }
                    
                    previousNote = currentNote
                    currentNote = note
                    
                    // Highlight the suggested note on virtual keyboard with light blue (like in the image)
                    if (note != null) {
                        highlightedNotes = highlightedNotes + (note.midiNote to KeyHighlightColor.LIGHT_BLUE)
                    }
                    
                    // Highlight the suggested note on RGB piano with blue
                    if (isConnected && note != null) {
                        val rgbController = midiConnectionManager.getRgbController()
                        rgbController?.highlightKeyBlue(note.midiNote)
                    }
                }
                
                override fun onCorrectNotePlayed(note: Note) {
                    Log.d(TAG, "Correct note played: ${note.midiNote}")
                    // Play piano sound for correct note (already played in MIDI callback)
                    // Highlight key with yellow on virtual keyboard (like in the image)
                    highlightedNotes = highlightedNotes + (note.midiNote to KeyHighlightColor.YELLOW)
                    // Clear highlight after a short delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        highlightedNotes = highlightedNotes - note.midiNote
                    }, 500)
                    // Highlight key with green on RGB piano
                    if (isConnected) {
                        val rgbController = midiConnectionManager.getRgbController()
                        rgbController?.highlightKeyGreen(note.midiNote)
                    }
                    // Visual feedback is handled by progress updates
                }
                
                override fun onIncorrectNotePlayed(playedNote: Int, expectedNote: Int) {
                    Log.d(TAG, "Incorrect note: played $playedNote, expected $expectedNote")
                    // Highlight incorrect key with red on virtual keyboard
                    highlightedNotes = highlightedNotes + (playedNote to KeyHighlightColor.RED)
                    // Clear highlight after a short delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        highlightedNotes = highlightedNotes - playedNote
                    }, 500)
                    // Highlight incorrect key with red on RGB piano
                    if (isConnected) {
                        val rgbController = midiConnectionManager.getRgbController()
                        rgbController?.highlightKeyRed(playedNote)
                        // Keep the expected note highlighted in blue
                        currentNote?.let { note ->
                            rgbController?.highlightKeyBlue(note.midiNote)
                        }
                    }
                    // Visual feedback is handled by progress updates
                }
                
                override fun onProgressUpdated(progress: LearningProgress) {
                    completedNotes = progress.completedNotes
                    currentNote = progress.currentNote
                    isPlaying = progress.state == LearningState.PLAYING
                }
                
                override fun onSongCompleted() {
                    Log.d(TAG, "Song completed!")
                    isPlaying = false
                }
                
                override fun onStateChanged(state: LearningState) {
                    isPlaying = state == LearningState.PLAYING
                    Log.d(TAG, "Learning state changed: $state")
                }
            }
        ).also { controller ->
            // Notify activity about controller creation
            onControllerCreated(controller)
            
            // Setup virtual MIDI input to route to learning controller
            if (!isConnected) {
                // Enable virtual mode if no device is connected
                isVirtualMode = true
                // Set virtual MIDI callback to route to controller
                midiConnectionManager.setInputCallback(object : MidiInputCallback {
                    override fun onNoteOn(note: Int, velocity: Int, channel: Int) {
                        controller.onNoteOn(note, velocity, channel)
                    }
                    
                    override fun onNoteOff(note: Int, velocity: Int, channel: Int) {
                        controller.onNoteOff(note, velocity, channel)
                    }
                    
                    override fun onMidiMessage(message: ByteArray, timestamp: Long) {
                        controller.onMidiMessage(message, timestamp)
                    }
                })
            }
        }
    }
    
    // Virtual MIDI input instance - routes directly to learning controller
    val virtualInput = remember(learningController) {
        VirtualMidiInput(learningController)
    }
    
    // Setup MIDI connection callbacks
    LaunchedEffect(Unit) {
        // Setup device discovery callback
        midiConnectionManager.setDiscoveryCallback(object : MidiDeviceDiscoveryCallback {
            override fun onDeviceFound(device: MidiDeviceWrapper) {
                // Update device list when a new device is found
                availableDevices = midiConnectionManager.getDiscoveredDevices()
            }
            
            override fun onDeviceRemoved(device: MidiDeviceWrapper) {
                // Update device list when a device is removed
                availableDevices = midiConnectionManager.getDiscoveredDevices()
            }
            
            override fun onScanComplete(devices: List<MidiDeviceWrapper>) {
                // Update device list when scan completes
                availableDevices = devices
                // Show device dialog after scan completes
                showDeviceDialog = true
            }
            
            override fun onScanError(error: String) {
                Log.e(TAG, "Scan error: $error")
                showDeviceDialog = false
            }
        })
        
        // Setup connection callback
        midiConnectionManager.setConnectionCallback(object : MidiConnectionCallback {
            override fun onConnected(device: MidiDeviceWrapper) {
                isConnected = true
                isVirtualMode = false
                showDeviceDialog = false
                Log.d(TAG, "Connected to device: ${device.name}")
                
                // IMPORTANT: Set up MIDI input callback to route events to learning controller
                // This allows the physical MIDI piano to control the app
                midiConnectionManager.setInputCallback(object : MidiInputCallback {
                    override fun onNoteOn(note: Int, velocity: Int, channel: Int) {
                        Log.d(TAG, "MIDI Note On received from device: note=$note, velocity=$velocity")
                        // Play sound immediately when MIDI note is received
                        soundPlayer.playNote(note, velocity)
                        // Highlight the pressed key with yellow on virtual keyboard
                        highlightedNotes = highlightedNotes + (note to KeyHighlightColor.YELLOW)
                        // Clear highlight after a short delay
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            highlightedNotes = highlightedNotes - note
                        }, 300)
                        // Also route to learning controller for learning mode
                        learningController.onNoteOn(note, velocity, channel)
                    }
                    
                    override fun onNoteOff(note: Int, velocity: Int, channel: Int) {
                        Log.d(TAG, "MIDI Note Off received from device: note=$note, velocity=$velocity")
                        learningController.onNoteOff(note, velocity, channel)
                    }
                    
                    override fun onMidiMessage(message: ByteArray, timestamp: Long) {
                        learningController.onMidiMessage(message, timestamp)
                    }
                })
                Log.d(TAG, "MIDI input callback set up for connected device")
            }
            
            override fun onDisconnected(device: MidiDeviceWrapper) {
                isConnected = false
                isVirtualMode = true // Enable virtual mode when disconnected
                learningController.pause()
                Log.d(TAG, "Disconnected from device: ${device.name} - enabling virtual mode")
                
                // Switch back to virtual MIDI input callback when disconnected
                // Virtual mode will use its own callback
                midiConnectionManager.setInputCallback(object : MidiInputCallback {
                    override fun onNoteOn(note: Int, velocity: Int, channel: Int) {
                        // Virtual mode - no action needed here as VirtualMidiInput handles it
                    }
                    
                    override fun onNoteOff(note: Int, velocity: Int, channel: Int) {
                        // Virtual mode - no action needed here as VirtualMidiInput handles it
                    }
                    
                    override fun onMidiMessage(message: ByteArray, timestamp: Long) {
                        // Virtual mode - no action needed here as VirtualMidiInput handles it
                    }
                })
            }
            
            override fun onConnectionError(error: String) {
                Log.e(TAG, "Connection error: $error")
                showDeviceDialog = false
            }
            
            override fun onReconnecting(device: MidiDeviceWrapper) {
                Log.d(TAG, "Reconnecting to device: ${device.name}")
            }
            
            override fun onReconnected(device: MidiDeviceWrapper) {
                isConnected = true
                Log.d(TAG, "Reconnected to device: ${device.name}")
            }
        })
        
        // Check initial connection status
        val initialConnected = midiConnectionManager.isConnected()
        isConnected = initialConnected
        
        // Enable virtual mode if no device is connected
        if (!initialConnected) {
            isVirtualMode = true
            Log.d(TAG, "No MIDI device connected - enabling virtual mode")
        }
    }
    
    // Handle Bluetooth enable dialog
    if (showBluetoothDialog) {
        BluetoothEnableDialog(
            onEnableClick = {
                showBluetoothDialog = false
                onRequestBluetoothEnable()
            },
            onDismiss = {
                showBluetoothDialog = false
            }
        )
    }
    
    // Handle device selection dialog
    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = availableDevices,
            onDeviceSelected = { device ->
                midiConnectionManager.connectToDevice(device)
            },
            onDismiss = {
                showDeviceDialog = false
            }
        )
    }
    
    PianoLearningScreen(
        songTitle = song.title,
        isConnected = isConnected,
        isVirtualMode = isVirtualMode,
        currentNote = currentNote,
        songNotes = song.notes,
        completedNotes = completedNotes,
        totalNotes = totalNotes,
        highlightedNotes = highlightedNotes,
        isPlaying = isPlaying,
        onConnectClick = {
            if (isConnected) {
                // Disconnect
                midiConnectionManager.disconnect()
                learningController.pause()
                isVirtualMode = true
            } else {
                // Check Bluetooth availability before scanning
                if (!BluetoothHelper.isBluetoothAvailable(context)) {
                    Log.w(TAG, "Bluetooth is not available on this device")
                    // Show message that Bluetooth is not available
                    // For now, still try to scan (USB devices might work)
                    midiConnectionManager.scanForDevices()
                    availableDevices = midiConnectionManager.getDiscoveredDevices()
                    showDeviceDialog = true
                } else if (!BluetoothHelper.isBluetoothEnabled(context)) {
                    // Bluetooth is available but not enabled
                    Log.d(TAG, "Bluetooth is not enabled, showing enable dialog")
                    showBluetoothDialog = true
                } else {
                    // Bluetooth is available and enabled, proceed with scan
                    Log.d(TAG, "Bluetooth is enabled, scanning for MIDI devices")
                    midiConnectionManager.scanForDevices()
                    availableDevices = midiConnectionManager.getDiscoveredDevices()
                    showDeviceDialog = true
                }
            }
        },
        onStartClick = {
            // Can start in virtual mode or connected mode
            learningController.start()
        },
        onPauseClick = {
            learningController.pause()
        },
        onResetClick = {
            // Turn off all RGB lights when resetting
            if (isConnected) {
                val rgbController = midiConnectionManager.getRgbController()
                rgbController?.turnOffAll()
            }
            // Clear all highlighted notes
            highlightedNotes = emptyMap()
            previousNote = null
            learningController.reset()
        },
        onNoteClick = { note ->
            // Always allow virtual keyboard to work, whether connected or not
            // Highlight the pressed key with yellow on virtual keyboard
            highlightedNotes = highlightedNotes + (note to KeyHighlightColor.YELLOW)
            // Clear highlight after a short delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                highlightedNotes = highlightedNotes - note
            }, 300)
            // Send virtual MIDI note when keyboard is tapped
            virtualInput.sendNoteOn(note, 100, 0)
            // Play piano sound
            soundPlayer.playNote(note, 100)
        }
    )
}
