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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.midi.pianomidi.ui.BluetoothEnableDialog
import com.midi.pianomidi.ui.DeviceSelectionDialog
import com.midi.pianomidi.ui.PianoLearningScreen
import com.midi.pianomidi.ui.DashboardScreen
import com.midi.pianomidi.KeyHighlightColor
import com.midi.pianomidi.ui.ConnectPianoScreen
import com.midi.pianomidi.ui.FreePlayScreen
import com.midi.pianomidi.ui.PianoSettingsDialog
import com.midi.pianomidi.SuperrServiceManager
import com.midi.pianomidi.ui.theme.PianomidiTheme
import com.midi.pianomidi.BluetoothHelper

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
        
        // Hide system bars (status bar and navigation bar)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
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
                DashboardScreenContent(
                    midiConnectionManager = midiConnectionManager!!,
                    onControllerCreated = { controller ->
                        this.learningModeController = controller
                    },
                    onRequestBluetoothEnable = {
                        requestBluetoothEnable()
                    }
                )
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

/**
 * Connect Piano Screen Content with Permission Handling
 * Wraps ConnectPianoScreen with permission request logic
 */
@Composable
fun ConnectPianoScreenContent(
    midiConnectionManager: MidiConnectionManager,
    onBackClick: () -> Unit,
    onConnected: () -> Unit,
    onConnectionFailed: (String) -> Unit,
    onRequestBluetoothEnable: () -> Unit
) {
    val context = LocalContext.current
    
    // Track if we should auto-start scan after permissions granted
    var shouldAutoStartScan by remember { mutableStateOf(false) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Log.d("ConnectPianoScreen", "Permissions granted")
            // Check Bluetooth state and proceed
            if (BluetoothHelper.isBluetoothEnabled(context)) {
                // Permissions granted and Bluetooth enabled - trigger scan
                shouldAutoStartScan = true
            } else {
                // Request Bluetooth enable
                onRequestBluetoothEnable()
            }
        } else {
            Log.w("ConnectPianoScreen", "Permissions denied")
            onConnectionFailed("Bluetooth permissions are required to scan for devices")
        }
    }
    
    // Bluetooth enable launcher
    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            Log.d("ConnectPianoScreen", "Bluetooth enabled")
            // Now start scanning
            shouldAutoStartScan = true
        } else {
            Log.d("ConnectPianoScreen", "Bluetooth enable cancelled")
            onConnectionFailed("Bluetooth must be enabled to scan for devices")
        }
    }
    
    // Auto-start scan when permissions/bluetooth are ready
    LaunchedEffect(shouldAutoStartScan) {
        if (shouldAutoStartScan) {
            shouldAutoStartScan = false
            // Small delay to ensure state is updated
            kotlinx.coroutines.delay(100)
        }
    }
    
    ConnectPianoScreen(
        midiConnectionManager = midiConnectionManager,
        onBackClick = onBackClick,
        onConnected = onConnected,
        onConnectionFailed = onConnectionFailed,
        onRequestPermissions = { permissions ->
            permissionLauncher.launch(permissions)
        },
        onRequestBluetoothEnable = {
            val intent = BluetoothHelper.getEnableBluetoothIntent()
            if (intent != null) {
                bluetoothEnableLauncher.launch(intent)
            } else {
                onRequestBluetoothEnable()
            }
        },
        autoStartScan = shouldAutoStartScan
    )
}

/**
 * Dashboard Screen Content with Navigation
 * Handles navigation between dashboard and feature screens
 */
@Composable
fun DashboardScreenContent(
    midiConnectionManager: MidiConnectionManager,
    onControllerCreated: (LearningModeController) -> Unit,
    onRequestBluetoothEnable: () -> Unit
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    var selectedSong by remember { mutableStateOf<com.midi.pianomidi.Song?>(null) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    
    // Piano Settings State
    var showSettingsDialog by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableStateOf(50) }
    var pianoTheme by remember { mutableStateOf(0) }
    var transpose by remember { mutableStateOf(0) }
    
    val superrServiceManager = remember { SuperrServiceManager.getInstance(context) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var availableDevices by remember { mutableStateOf<List<MidiDeviceWrapper>>(emptyList()) }
    
    // Setup device discovery callback
    LaunchedEffect(Unit) {
        midiConnectionManager.setDiscoveryCallback(object : MidiDeviceDiscoveryCallback {
            override fun onDeviceFound(device: MidiDeviceWrapper) {
                availableDevices = midiConnectionManager.getDiscoveredDevices()
            }
            
            override fun onDeviceRemoved(device: MidiDeviceWrapper) {
                availableDevices = midiConnectionManager.getDiscoveredDevices()
            }
            
            override fun onScanComplete(devices: List<MidiDeviceWrapper>) {
                availableDevices = devices
                showDeviceDialog = true
            }
            
            override fun onScanError(error: String) {
                Log.e("DashboardScreen", "Scan error: $error")
                showDeviceDialog = false
            }
        })
    }
    
    // Handle dialogs
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
    
    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = availableDevices,
            onDeviceSelected = { device ->
                midiConnectionManager.connectToDevice(device)
                showDeviceDialog = false
            },
            onDismiss = {
                showDeviceDialog = false
            }
        )
    }

    if (showSettingsDialog) {
        PianoSettingsDialog(
            onDismiss = { showSettingsDialog = false },
            superrServiceManager = superrServiceManager,
            initialSensitivity = sensitivity,
            initialTheme = pianoTheme,
            initialTranspose = transpose,
            onSettingsChanged = { s, t, tr ->
                sensitivity = s
                pianoTheme = t
                transpose = tr
            }
        )
    }
    
    when (currentScreen) {
        Screen.Dashboard -> {
            DashboardScreen(
                midiConnectionManager = midiConnectionManager,
                onNavigateToLearning = {
                    currentScreen = Screen.Learning
                },
                onNavigateToFreePlay = {
                    currentScreen = Screen.FreePlay
                },
                onNavigateToSongLibrary = {
                    currentScreen = Screen.SongLibrary
                },
                onNavigateToSettings = {
                    showSettingsDialog = true
                },
                onManageConnection = {
                    // Navigate to Connect Piano screen
                    currentScreen = Screen.ConnectPiano
                }
            )
        }
        Screen.Learning -> {
            PianoLearningScreenContent(
                midiConnectionManager = midiConnectionManager,
                onControllerCreated = onControllerCreated,
                onRequestBluetoothEnable = onRequestBluetoothEnable,
                onNavigateBack = {
                    currentScreen = Screen.Dashboard
                },
                onSettingsClick = {
                    showSettingsDialog = true
                },
                initialSong = selectedSong
            )
        }
        Screen.FreePlay -> {
            FreePlayScreenContent(
                midiConnectionManager = midiConnectionManager,
                onBackClick = {
                    currentScreen = Screen.Dashboard
                },
                onSettingsClick = {
                    showSettingsDialog = true
                }
            )
        }
        
        Screen.SongLibrary -> {
            com.midi.pianomidi.ui.SongLibraryScreen(
                onNavigateBack = {
                    currentScreen = Screen.Dashboard
                },
                onSongSelect = { song ->
                    selectedSong = song
                    currentScreen = Screen.Learning // Re-use learning as player for now
                },
                onNavigateToHome = {
                    currentScreen = Screen.Dashboard
                },
                onNavigateToSettings = {
                    showSettingsDialog = true
                }
            )
        }
        
        Screen.ConnectPiano -> {
            ConnectPianoScreenContent(
                midiConnectionManager = midiConnectionManager,
                onBackClick = {
                    currentScreen = Screen.Dashboard
                },
                onConnected = {
                    // Navigate back to dashboard on successful connection
                    currentScreen = Screen.Dashboard
                },
                onConnectionFailed = { error ->
                    // Show error - for now just log it
                    // TODO: Show error snackbar/toast
                    Log.e("ConnectPianoScreen", "Connection failed: $error")
                },
                onRequestBluetoothEnable = {
                    onRequestBluetoothEnable()
                }
            )
        }
    }
}

/**
 * Screen enum for navigation
 */
private enum class Screen {
    Dashboard,
    Learning,
    FreePlay,
    ConnectPiano,
    SongLibrary
}

@Composable
fun PianoLearningScreenContent(
    midiConnectionManager: MidiConnectionManager,
    onControllerCreated: (LearningModeController) -> Unit,
    onRequestBluetoothEnable: () -> Unit,
    onNavigateBack: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    initialSong: com.midi.pianomidi.Song? = null,
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
    val song = remember(initialSong) { initialSong ?: TwinkleTwinkleLittleStar.createSong() }
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
        onNavigateBack = onNavigateBack,
        onSettingsClick = onSettingsClick,
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

/**
 * Free Play Screen Content
 * Wraps FreePlayScreen with sound player initialization
 */
@Composable
fun FreePlayScreenContent(
    midiConnectionManager: MidiConnectionManager,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
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
    
    FreePlayScreen(
        midiConnectionManager = midiConnectionManager,
        soundPlayer = soundPlayer,
        onBackClick = onBackClick,
        onSettingsClick = onSettingsClick,
        modifier = modifier
    )
}
