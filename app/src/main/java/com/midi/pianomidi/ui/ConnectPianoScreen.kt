package com.midi.pianomidi.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import com.midi.pianomidi.BluetoothHelper
import com.midi.pianomidi.MidiConnectionManager
import com.midi.pianomidi.MidiConnectionCallback
import com.midi.pianomidi.MidiDeviceDiscoveryCallback
import com.midi.pianomidi.MidiDeviceWrapper
import com.midi.pianomidi.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom Bluetooth Icon Component
 * Draws a Bluetooth symbol (B-shaped) using Canvas
 */
@Composable
fun BluetoothIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerX = width / 2
        val centerY = height / 2
        val strokeWidth = width * 0.1f
        
        // Bluetooth symbol: Simplified B-shape with curves
        val path = Path().apply {
            // Start from top-left
            moveTo(centerX - width * 0.2f, centerY - height * 0.3f)
            
            // Top-left curve
            cubicTo(
                centerX - width * 0.3f, centerY - height * 0.4f,
                centerX - width * 0.25f, centerY - height * 0.35f,
                centerX - width * 0.2f, centerY - height * 0.25f
            )
            
            // Vertical line down
            lineTo(centerX - width * 0.2f, centerY)
            
            // Diagonal to right (middle)
            lineTo(centerX + width * 0.2f, centerY - height * 0.15f)
            
            // Bottom-right curve
            cubicTo(
                centerX + width * 0.25f, centerY - height * 0.05f,
                centerX + width * 0.2f, centerY + height * 0.05f,
                centerX + width * 0.15f, centerY + height * 0.1f
            )
            
            // Back to center
            lineTo(centerX - width * 0.2f, centerY)
            
            // Bottom-left curve
            cubicTo(
                centerX - width * 0.25f, centerY + height * 0.35f,
                centerX - width * 0.3f, centerY + height * 0.4f,
                centerX - width * 0.2f, centerY + height * 0.3f
            )
            
            // Complete top-right curve
            moveTo(centerX - width * 0.2f, centerY - height * 0.1f)
            lineTo(centerX + width * 0.2f, centerY - height * 0.25f)
            cubicTo(
                centerX + width * 0.25f, centerY - height * 0.3f,
                centerX + width * 0.2f, centerY - height * 0.35f,
                centerX + width * 0.15f, centerY - height * 0.3f
            )
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

/**
 * Connect Piano Screen - Main entry point for MIDI device connection
 * 
 * INTEGRATION POINTS:
 * - Uses MidiConnectionManager.scanForDevices() to start scanning
 * - Listens to MidiDeviceDiscoveryCallback for scan state updates
 * - Listens to MidiConnectionCallback for connection state
 * - Auto-connects to first found device (or shows selection if multiple)
 */
@Composable
fun ConnectPianoScreen(
    midiConnectionManager: MidiConnectionManager,
    onBackClick: () -> Unit = {},
    onConnected: () -> Unit = {},
    onConnectionFailed: (String) -> Unit = {},
    onRequestPermissions: ((Array<String>) -> Unit)? = null,
    onRequestBluetoothEnable: (() -> Unit)? = null,
    autoStartScan: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    // Calculate responsive sizes based on screen dimensions
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    
    // Responsive animation size - adapts to screen size
    val animationSize = remember(screenHeight, screenWidth) {
        if (isLandscape) {
            // Landscape: use width-based sizing
            (screenWidth * 0.25f).coerceIn(120.dp, 200.dp)
        } else {
            // Portrait: use height-based sizing
            (screenHeight * 0.2f).coerceIn(150.dp, 250.dp)
        }
    }
    
    // Handle system back button
    BackHandler(onBack = onBackClick)
    
    // UI State
    var isScanning by remember { mutableStateOf(false) }
    var foundDevices by remember { mutableStateOf<List<MidiDeviceWrapper>>(emptyList()) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    
    // Check if permissions are granted
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Function to start scanning with all checks
    fun startScanWithChecks() {
        // Check permissions first
        if (!hasBluetoothPermissions()) {
            showPermissionDialog = true
            return
        }
        
        // Check if Bluetooth is enabled
        if (!BluetoothHelper.isBluetoothEnabled(context)) {
            showBluetoothDialog = true
            return
        }
        
        // All checks passed, start scanning
        isScanning = true
        connectionError = null
        foundDevices = emptyList()
        midiConnectionManager.scanForDevices()
    }
    
    // Auto-start scan when permissions/bluetooth are ready
    LaunchedEffect(autoStartScan) {
        if (autoStartScan && !isScanning) {
            startScanWithChecks()
        }
    }
    
    // Track scanning state and handle device discovery
    LaunchedEffect(Unit) {
        midiConnectionManager.setDiscoveryCallback(object : MidiDeviceDiscoveryCallback {
            override fun onDeviceFound(device: MidiDeviceWrapper) {
                foundDevices = midiConnectionManager.getDiscoveredDevices()
            }
            
            override fun onDeviceRemoved(device: MidiDeviceWrapper) {
                foundDevices = midiConnectionManager.getDiscoveredDevices()
            }
            
            override fun onScanComplete(devices: List<MidiDeviceWrapper>) {
                // Scan completed, stop scanning animation
                isScanning = false
                foundDevices = devices
            }
            
            override fun onScanError(error: String) {
                isScanning = false
                if (error.contains("Permission", ignoreCase = true)) {
                    // Request permissions if missing
                    showPermissionDialog = true
                } else {
                    connectionError = error
                    onConnectionFailed(error)
                }
            }
        })
        
        midiConnectionManager.setConnectionCallback(object : MidiConnectionCallback {
            override fun onConnected(device: MidiDeviceWrapper) {
                isScanning = false
                onConnected()
            }
            
            override fun onDisconnected(device: MidiDeviceWrapper) {
                // Handle disconnection if needed
            }
            
            override fun onConnectionError(error: String) {
                isScanning = false
                isConnecting = false
                connectionError = error
                onConnectionFailed(error)
            }
            
            override fun onReconnecting(device: MidiDeviceWrapper) {
                isConnecting = true
            }
            override fun onReconnected(device: MidiDeviceWrapper) {
                isScanning = false
                isConnecting = false
                onConnected()
            }
        })
    }
    
    // Show error snackbar if connection failed
    connectionError?.let { error ->
        LaunchedEffect(error) {
            // Error will be handled by onConnectionFailed callback
            connectionError = null
        }
    }
    
    // Permission Dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = {
                Text(
                    text = "Bluetooth Permission Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This app needs Bluetooth permissions to scan for MIDI devices. Please grant the required permissions.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        // Request permissions
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val permissions = arrayOf(
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                            )
                            onRequestPermissions?.invoke(permissions)
                        } else {
                            val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                            onRequestPermissions?.invoke(permissions)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Bluetooth Enable Dialog
    if (showBluetoothDialog) {
        AlertDialog(
            onDismissRequest = { showBluetoothDialog = false },
            title = {
                Text(
                    text = "Enable Bluetooth",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Bluetooth is required to connect to MIDI devices. Please enable Bluetooth to continue.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBluetoothDialog = false
                        onRequestBluetoothEnable?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonGreen,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Enable Bluetooth", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBluetoothDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground,
                        Color(0xFF0F0F0F)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top Bar - Fixed height
            ConnectPianoHeader(
                onBackClick = onBackClick,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Central Content Area - Scrollable and responsive
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(
                        state = rememberScrollState(),
                        enabled = true
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Responsive spacing based on screen size
                val verticalSpacing = if (isLandscape) 16.dp else 24.dp
                
                BluetoothScannerAnimation(
                    isScanning = isScanning,
                    modifier = Modifier.size(animationSize)
                )
                
                Spacer(modifier = Modifier.height(verticalSpacing))
                
                // Status Text - Responsive font size
                val titleFontSize = remember(screenHeight) {
                    if (screenHeight < 600.dp) 18.sp else 22.sp
                }
                
                Text(
                    text = when {
                        isConnecting -> "Connecting..."
                        foundDevices.isNotEmpty() -> "Found ${foundDevices.size} Device(s)"
                        isScanning -> "Looking for Piano..."
                        else -> "Ready to Connect"
                    },
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
                
                val bodyFontSize = remember(screenHeight) {
                    if (screenHeight < 600.dp) 12.sp else 14.sp
                }
                
                Text(
                    text = when {
                        isConnecting -> "Please wait while we connect to your device..."
                        foundDevices.isNotEmpty() -> "Tap on a device to connect"
                        isScanning -> "Ensure your MIDI device is powered on and within Bluetooth range."
                        else -> "Tap 'Scan & Connect' to find your MIDI piano"
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = bodyFontSize,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center
                )
                
                // Device List - Show found devices as round buttons
                if (foundDevices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(verticalSpacing))
                    
                    // Device buttons in a scrollable row with responsive sizing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        foundDevices.forEach { device ->
                            DeviceRoundButton(
                                device = device,
                                onClick = {
                                    if (!isConnecting) {
                                        isConnecting = true
                                        isScanning = false
                                        midiConnectionManager.stopScan()
                                        // INTEGRATION: Connect to selected device
                                        midiConnectionManager.connectToDevice(device)
                                    }
                                },
                                isConnecting = isConnecting
                            )
                        }
                    }
                }
                
                // Extra spacing at bottom of scrollable area to ensure button visibility
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Bottom Action Area - Fixed at bottom, always visible with responsive padding
            val bottomPadding = remember(screenHeight) {
                if (screenHeight < 600.dp) 8.dp else 16.dp
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = bottomPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Scan & Connect Button - Always show, allow stop scan when scanning
                if (isConnecting) {
                    // Show connecting state
                    Button(
                        onClick = { /* Disabled */ },
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen.copy(alpha = 0.5f),
                            contentColor = Color.Black
                        )
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Connecting...",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else {
                    ScanConnectButton(
                        isScanning = isScanning,
                        onClick = {
                            if (isScanning) {
                                // Stop scanning
                                isScanning = false
                                midiConnectionManager.stopScan()
                            } else {
                                // Check permissions and Bluetooth before scanning
                                startScanWithChecks()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Troubleshooting Link
                Text(
                    text = "Having trouble connecting?",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 14.sp,
                        color = TextSecondary.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.clickable {
                        // TODO: Show troubleshooting dialog or navigate to help screen
                    }
                )
            }
        }
    }
}

/**
 * Header Bar Component
 * Top navigation with back arrow and title
 */
@Composable
fun ConnectPianoHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Arrow
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Title
        Text(
            text = "Connect Piano",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Bluetooth Scanner Animation Component
 * 
 * Features:
 * - Central Bluetooth icon with neon green glow
 * - Pulsating rings (3 concentric circles)
 * - Orbiting dots around rings (scan animation)
 * - Animation runs when isScanning == true
 */
@Composable
fun BluetoothScannerAnimation(
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    // Animation values for pulsing rings
    val infiniteTransition = rememberInfiniteTransition(label = "bluetooth_scan")
    
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring1_alpha"
    )
    
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring2_alpha"
    )
    
    val ring3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring3_alpha"
    )
    
    // Rotation animation for orbiting dots
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val maxRadius = size.minDimension / 2
            
            if (isScanning) {
                // Outer ring (Ring 3) - largest, darkest
                drawCircle(
                    color = NeonGreen.copy(alpha = ring3Alpha),
                    radius = maxRadius * 0.9f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 2.dp.toPx())
                )
                
                // Middle ring (Ring 2) - medium, yellow-green
                drawCircle(
                    color = Color(0xFF88FF00).copy(alpha = ring2Alpha), // Yellow-green
                    radius = maxRadius * 0.7f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 3.dp.toPx())
                )
                
                // Inner ring (Ring 1) - smallest, brightest neon green
                drawCircle(
                    color = NeonGreen.copy(alpha = ring1Alpha),
                    radius = maxRadius * 0.5f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 4.dp.toPx())
                )
                
                // Orbiting dots around rings
                val dotColors = listOf(
                    Color(0xFFFFD700), // Yellow
                    Color(0xFFFF6B35), // Orange
                    NeonGreen // Green
                )
                
                // Dots on outer ring
                for (i in 0..7) {
                    val angle = (i * 45f + rotationAngle) * (Math.PI / 180f).toFloat()
                    val radius = maxRadius * 0.85f
                    val x = centerX + cos(angle) * radius
                    val y = centerY + sin(angle) * radius
                    
                    drawCircle(
                        color = dotColors[i % dotColors.size].copy(alpha = 0.8f),
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
                
                // Dots on middle ring
                for (i in 0..5) {
                    val angle = (i * 60f + rotationAngle * 1.5f) * (Math.PI / 180f).toFloat()
                    val radius = maxRadius * 0.65f
                    val x = centerX + cos(angle) * radius
                    val y = centerY + sin(angle) * radius
                    
                    drawCircle(
                        color = dotColors[i % dotColors.size].copy(alpha = 0.7f),
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
        
        // Central Bluetooth Icon Circle
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glow effect
            if (isScanning) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NeonGreen.copy(alpha = 0.3f),
                                    NeonGreen.copy(alpha = 0f)
                                )
                            ),
                            shape = RoundedCornerShape(60.dp)
                        )
                )
            }
            
            // Main circle
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        color = if (isScanning) NeonGreen else NeonGreen.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Bluetooth Icon - Custom drawn
                BluetoothIcon(
                    modifier = Modifier.size(48.dp),
                    color = Color.Black
                )
            }
        }
    }
}

/**
 * Scan & Connect Button Component
 * Large neon green button with icon
 */
@Composable
fun ScanConnectButton(
    isScanning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = true,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonGreen,
            contentColor = Color.Black
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (isScanning) {
            Icon(
                imageVector = Icons.Default.ExitToApp,
                contentDescription = "Stop",
                modifier = Modifier.size(20.dp),
                tint = Color.Black
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Stop Scan",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        } else {
            BluetoothIcon(
                modifier = Modifier.size(20.dp),
                color = Color.Black
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Scan & Connect",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

