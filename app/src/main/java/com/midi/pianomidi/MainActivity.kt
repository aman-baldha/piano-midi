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
import com.midi.pianomidi.ui.*
import com.midi.pianomidi.ui.theme.PianomidiTheme
import com.midi.pianomidi.BluetoothHelper
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_ENABLE_BLUETOOTH = 1
    }
    
    private var midiConnectionManager: MidiConnectionManager? = null
    
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
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
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
    onRequestBluetoothEnable: () -> Unit
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
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
                    // Learning mode removed
                },
                onNavigateToFreePlay = {
                    currentScreen = Screen.FreePlay
                },
                onNavigateToSongLibrary = {
                    // Song library removed
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
            // Screen removed
            currentScreen = Screen.Dashboard
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
            // Screen removed
            currentScreen = Screen.Dashboard
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
