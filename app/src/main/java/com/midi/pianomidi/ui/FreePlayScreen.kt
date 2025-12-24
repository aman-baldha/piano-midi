package com.midi.pianomidi.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midi.pianomidi.MidiConnectionManager
import com.midi.pianomidi.MidiInputCallback
import com.midi.pianomidi.NoteVisualizerState
import com.midi.pianomidi.KeyHighlightColor
import com.midi.pianomidi.PianoNoteHandler
import com.midi.pianomidi.PianoSoundPlayer
import com.midi.pianomidi.SuperrServiceManager
import com.midi.pianomidi.ui.theme.*
import com.midi.pianomidi.ui.PianoKeyboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Free Play Screen - Allows users to play piano freely without guided lessons
 * Features:
 * - Virtual piano keyboard (C3-C5 octave range)
 * - Octave controls
 * - Tempo/BPM display
 * - Visualizers for active notes
 * - MIDI device support
 * - Sound playback
 */
@Composable
fun FreePlayScreen(
    midiConnectionManager: MidiConnectionManager,
    soundPlayer: PianoSoundPlayer,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // State management
    var isConnected by remember { mutableStateOf(false) }
    var deviceName by remember { mutableStateOf<String?>(null) }
    var currentOctaveStart by remember { mutableStateOf(48) } // C3 = 48
    var tempo by remember { mutableStateOf(120) } // BPM
    var activeNotes by remember { mutableStateOf<Map<Int, NoteVisualizerState>>(emptyMap()) }
    var currentNote by remember { mutableStateOf<Int?>(null) }
    var highlightedNotes by remember { mutableStateOf<Map<Int, KeyHighlightColor>>(emptyMap()) }
    
    // Superr Service settings
    var sensitivity by remember { mutableStateOf(50) } // 0-100, default 50
    var theme by remember { mutableStateOf(0) } // 0=Aurora, 1=Fire, 2=Matrix
    var transpose by remember { mutableStateOf(0) } // -12 to +12
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    // Initialize PianoNoteHandler (single point of truth for note processing)
    val noteHandler = remember {
        PianoNoteHandler(soundPlayer, midiConnectionManager)
    }
    
    // Initialize Superr Service Manager
    val superrServiceManager = remember {
        SuperrServiceManager.getInstance(context)
    }
    
    // Particle animation state for upward flowing colors
    var particles by remember { mutableStateOf<List<ColorParticle>>(emptyList()) }
    
    // Track current time for visualizer animation
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val configuration = LocalConfiguration.current
    val density = configuration.densityDpi / 160f
    val screenWidthPx = remember { configuration.screenWidthDp * density }
    val screenHeightPx = remember { configuration.screenHeightDp * density }
    val coroutineScope = rememberCoroutineScope()
    
    // Setup MIDI connection callbacks
    LaunchedEffect(Unit) {
        // Check initial connection status
        isConnected = midiConnectionManager.isConnected()
        deviceName = midiConnectionManager.getCurrentDevice()?.name
        
        // Connect to Superr Service if device is connected via BLE
        val bluetoothDevice = midiConnectionManager.getBluetoothDevice()
        if (bluetoothDevice != null) {
            superrServiceManager.connectToDevice(bluetoothDevice)
            // Set default values
            superrServiceManager.setSensitivity(sensitivity)
            superrServiceManager.setTheme(theme)
            superrServiceManager.setTranspose(transpose)
        }
        
        // Refresh note handler connection
        noteHandler.refreshConnection()
        
        // Setup connection callback
        midiConnectionManager.setConnectionCallback(object : com.midi.pianomidi.MidiConnectionCallback {
            override fun onConnected(device: com.midi.pianomidi.MidiDeviceWrapper) {
                isConnected = true
                deviceName = device.name
                
                // Connect to Superr Service
                device.bluetoothDevice?.let {
                    superrServiceManager.connectToDevice(it)
                    // Set current settings
                    superrServiceManager.setSensitivity(sensitivity)
                    superrServiceManager.setTheme(theme)
                    superrServiceManager.setTranspose(transpose)
                }
                
                // Refresh note handler
                noteHandler.refreshConnection()
            }
            
            override fun onDisconnected(device: com.midi.pianomidi.MidiDeviceWrapper) {
                isConnected = false
                deviceName = null
                superrServiceManager.disconnect()
                noteHandler.refreshConnection()
            }
            
            override fun onConnectionError(error: String) {
                // Handle error if needed
            }
            
            override fun onReconnecting(device: com.midi.pianomidi.MidiDeviceWrapper) {
                // Handle reconnecting
            }
            
            override fun onReconnected(device: com.midi.pianomidi.MidiDeviceWrapper) {
                isConnected = true
                deviceName = device.name
                
                // Reconnect to Superr Service
                device.bluetoothDevice?.let {
                    superrServiceManager.connectToDevice(it)
                    superrServiceManager.setSensitivity(sensitivity)
                    superrServiceManager.setTheme(theme)
                    superrServiceManager.setTranspose(transpose)
                }
                
                noteHandler.refreshConnection()
            }
        })
        
        // Setup MIDI input callback for physical device - routes through noteHandler
        midiConnectionManager.setInputCallback(object : MidiInputCallback {
            override fun onNoteOn(note: Int, velocity: Int, channel: Int) {
                // Process through centralized note handler
                noteHandler.onNoteOn(note, velocity, channel)
                
                // Update current note for keyboard display
                currentNote = note
                
                // Create upward flowing particles for this note
                val colors = listOf(
                    Color(0xFFFF6B35), // Orange
                    Color(0xFFFFEB3B), // Yellow
                    NeonGreen          // Green
                )
                val noteColor = colors[note % colors.size]
                
                // Add to active notes for visualizer with color
                activeNotes = activeNotes + (note to NoteVisualizerState(
                    note = note,
                    velocity = velocity,
                    startTime = System.currentTimeMillis(),
                    color = noteColor
                ))
                
                // Create multiple particles flowing upward from bottom of screen
                val barWidth = screenWidthPx / 25f
                val particleX = (note % 25) * barWidth + barWidth / 2
                val now = System.currentTimeMillis()
                
                val newParticles = (0 until 15).map { i ->
                    ColorParticle(
                        x = particleX,
                        y = screenHeightPx, // Start from bottom
                        color = noteColor,
                        velocity = 2f + (velocity / 127f) * 3f,
                        size = 4f + (velocity / 127f) * 6f,
                        alpha = 0.8f - (i * 0.05f),
                        startTime = now + (i * 10L)
                    )
                }
                particles = particles + newParticles
            }
            
            override fun onNoteOff(note: Int, velocity: Int, channel: Int) {
                // Process through centralized note handler
                noteHandler.onNoteOff(note, velocity, channel)
                
                // Clear current note if it's the one being released
                if (currentNote == note) {
                    currentNote = null
                }
                
                // Remove from active notes after a longer delay to allow for slower rising animation
                coroutineScope.launch {
                    delay(4000) // 4 seconds to rise and fade
                    activeNotes = activeNotes - note
                }
            }
            
            override fun onMidiMessage(message: ByteArray, timestamp: Long) {
                // Handle raw MIDI messages if needed
            }
        })
    }
    
    // Update Superr Service when settings change
    LaunchedEffect(sensitivity) {
        if (superrServiceManager.isConnected()) {
            superrServiceManager.setSensitivity(sensitivity)
        }
    }
    
    LaunchedEffect(theme) {
        if (superrServiceManager.isConnected()) {
            superrServiceManager.setTheme(theme)
        }
    }
    
    LaunchedEffect(transpose) {
        if (superrServiceManager.isConnected()) {
            superrServiceManager.setTranspose(transpose)
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            noteHandler.cleanup()
            superrServiceManager.cleanup()
        }
    }
    
    // Update time periodically for visualizer animation and particles
    LaunchedEffect(Unit) {
        while (true) {
            delay(16) // ~60fps
            val now = System.currentTimeMillis()
            currentTime = now
            
            // Update particles - move upward and remove when off screen
            particles = particles.mapNotNull { particle ->
                val elapsed = now - particle.startTime
                if (elapsed >= 0) {
                    val distance = particle.velocity * (elapsed / 16f)
                    val newY = particle.y - distance
                    val newAlpha = particle.alpha * (1f - elapsed / 2000f) // Fade over 2 seconds
                    
                    if (newY > -100f && newAlpha > 0.1f) {
                        particle.copy(
                            y = newY,
                            alpha = newAlpha.coerceIn(0f, 1f)
                        )
                    } else null
                } else {
                    particle // Keep particle if not started yet
                }
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Grid background pattern
        GridBackgroundPattern()
        
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar with Octave Controls
            FreePlayTopBar(
                isConnected = isConnected,
                deviceName = deviceName,
                currentOctaveStart = currentOctaveStart,
                tempo = tempo,
                onBackClick = onBackClick,
                onOctaveDecrease = {
                    if (currentOctaveStart > 24) { // Minimum C2
                        currentOctaveStart -= 12
                    }
                },
                onOctaveIncrease = {
                    if (currentOctaveStart < 72) { // Maximum C6
                        currentOctaveStart += 12
                    }
                },
                onSettingsClick = {
                    showSettingsDialog = true
                    onSettingsClick()
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Main content area with visualizer and particles
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Visualizer bars
                NoteVisualizer(
                    activeNotes = activeNotes,
                    currentTime = currentTime,
                    octaveStart = currentOctaveStart,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Upward flowing color particles
                ColorFlowParticles(
                    particles = particles,
                    currentTime = currentTime,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Piano Keyboard at the bottom
            PianoKeyboard(
                currentNote = currentNote,
                highlightedNotes = highlightedNotes,
                octaveStart = currentOctaveStart,
                onNoteClick = { note ->
                    // Play note when keyboard is tapped
                    val velocity = 100
                    
                    // Highlight the pressed key
                    highlightedNotes = highlightedNotes + (note to KeyHighlightColor.YELLOW)
                    
                    // Clear highlight after a short delay
                    coroutineScope.launch {
                        delay(300)
                        highlightedNotes = highlightedNotes - note
                    }
                    
                    // Update current note
                    currentNote = note
                    
                    // Process through note handler
                    noteHandler.onNoteOn(note, velocity, 0)
                    
                    // Create upward flowing particles for this note
                    val colors = listOf(
                        Color(0xFFFF6B35), // Orange
                        Color(0xFFFFEB3B), // Yellow
                        NeonGreen          // Green
                    )
                    val noteColor = colors[note % colors.size]

                    // Add to active notes for visualizer with color
                    activeNotes = activeNotes + (note to NoteVisualizerState(
                        note = note,
                        velocity = velocity,
                        startTime = System.currentTimeMillis(),
                        color = noteColor
                    ))
                    
                    // Create multiple particles flowing upward from bottom of screen
                    val barWidth = screenWidthPx / 25f
                    val particleX = (note % 25) * barWidth + barWidth / 2
                    val now = System.currentTimeMillis()
                    
                    val newParticles = (0 until 15).map { i ->
                        ColorParticle(
                            x = particleX,
                            y = screenHeightPx, // Start from bottom
                            color = noteColor,
                            velocity = 2f + (velocity / 127f) * 3f,
                            size = 4f + (velocity / 127f) * 6f,
                            alpha = 0.8f - (i * 0.05f),
                            startTime = now + (i * 10L)
                        )
                    }
                    particles = particles + newParticles
                    
                    // Release note after a short delay (simulate key press)
                    coroutineScope.launch {
                        delay(500)
                        noteHandler.onNoteOff(note, 0, 0)
                        if (currentNote == note) {
                            currentNote = null
                        }
                    }
                    
                    // Remove from visualizer after a longer delay
                    coroutineScope.launch {
                        delay(4000) // 4 seconds to rise and fade
                        activeNotes = activeNotes - note
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
            
            // Settings Dialog
            if (showSettingsDialog) {
                FreePlaySettingsDialog(
                    sensitivity = sensitivity,
                    theme = theme,
                    transpose = transpose,
                    onSensitivityChange = { sensitivity = it },
                    onThemeChange = { theme = it },
                    onTransposeChange = { transpose = it },
                    onDismiss = { showSettingsDialog = false }
                )
            }
        }
    }
}

/**
 * Data class for note visualizer state
 */
// NoteVisualizerState removed - now using shared model from MidiModels.kt


/**
 * Top bar with back button, title, connection status, octave controls, and settings
 */
@Composable
fun FreePlayTopBar(
    isConnected: Boolean,
    deviceName: String?,
    currentOctaveStart: Int,
    tempo: Int,
    onBackClick: () -> Unit,
    onOctaveDecrease: () -> Unit,
    onOctaveIncrease: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // First row: Back, Title, Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Title and connection status
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FREE PLAY",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Connection indicator dot (green dot when connected)
                    if (isConnected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeonGreen)
                        )
                    }
                    
                    Text(
                        text = if (isConnected) {
                            "${deviceName?.uppercase() ?: "LUMI"} CONNECTED"
                        } else {
                            "NOT CONNECTED"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = if (isConnected) NeonGreen else TextSecondary
                        )
                    )
                }
            }
            
            // Octave controls and Tempo container (to the left of settings)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2A2A)) // Dark gray background
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left decrease button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3A3A)) // Dark gray circular button
                        .clickable { onOctaveDecrease() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Decrease Octave",
                        tint = Color(0xFFCCCCCC), // Light gray
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // Octave display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "OCTAVE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            color = Color(0xFF999999) // Muted gray
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val startNote = currentOctaveStart
                    val endNote = currentOctaveStart + 24 // 2 octaves
                    Text(
                        text = "${getNoteNameWithOctave(startNote)} - ${getNoteNameWithOctave(endNote)}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFEB3B) // Bright yellow
                        )
                    )
                }
                
                // Right increase button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3A3A3A)) // Dark gray circular button
                        .clickable { onOctaveIncrease() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Increase Octave",
                        tint = Color(0xFFCCCCCC), // Light gray
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Settings button
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


/**
 * Visualizer showing glowing bars for active notes
 * Matches the design with vertical glowing bars rising from bottom
 */
@Composable
fun NoteVisualizer(
    activeNotes: Map<Int, NoteVisualizerState>,
    currentTime: Long,
    octaveStart: Int = 48,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val totalWhiteKeys = 14 // 2 octaves
        val barWidth = size.width / totalWhiteKeys
        
        // Draw vertical bars for each active note
        activeNotes.values.forEach { state ->
            val note = state.note
            val velocity = state.velocity
            val elapsed = currentTime - state.startTime
            
            // Rising logic: move upward over time
            // Speed slowed down significantly for smooth animation
            val speed = 0.15f + (velocity / 127f) * 0.25f
            val yOffset = (elapsed * speed) // Removed modulo (%) to prevent repetition
            val currentY = size.height - yOffset
            
            // Map note to its horizontal position above the corresponding piano key
            // totalWhiteKeys = 14 (2 octaves)
            val whiteKeyWidth = size.width / 14
            val octaveNote = note % 12
            val relativeNote = note - octaveStart
            val octaveIndex = relativeNote / 12
            
            val x = when (octaveNote) {
                0 -> (octaveIndex * 7 + 0.5f) * whiteKeyWidth // C
                2 -> (octaveIndex * 7 + 1.5f) * whiteKeyWidth // D
                4 -> (octaveIndex * 7 + 2.5f) * whiteKeyWidth // E
                5 -> (octaveIndex * 7 + 3.5f) * whiteKeyWidth // F
                7 -> (octaveIndex * 7 + 4.5f) * whiteKeyWidth // G
                9 -> (octaveIndex * 7 + 5.5f) * whiteKeyWidth // A
                11 -> (octaveIndex * 7 + 6.5f) * whiteKeyWidth // B
                // Black keys
                1 -> (octaveIndex * 7 + 1.0f) * whiteKeyWidth // C#
                3 -> (octaveIndex * 7 + 2.0f) * whiteKeyWidth // D#
                6 -> (octaveIndex * 7 + 4.0f) * whiteKeyWidth // F#
                8 -> (octaveIndex * 7 + 5.0f) * whiteKeyWidth // G#
                10 -> (octaveIndex * 7 + 6.0f) * whiteKeyWidth // A#
                else -> (octaveIndex * 7 + 0.5f) * whiteKeyWidth
            }
            
            // Calculate bar dimensions - "pill" shape
            val pillWidth = barWidth * 0.5f
            val pillHeight = (40.dp.toPx() + (velocity / 127f) * 60.dp.toPx()).coerceAtLeast(40.dp.toPx())
            
            // Aurora / Breathing effect
            val breatheCycle = (elapsed / 1000f) * Math.PI * 2
            val breatheAlpha = 0.7f + (Math.sin(breatheCycle).toFloat() * 0.2f)
            val fadeOut = (1f - (yOffset / size.height)).coerceIn(0f, 1f)
            val finalAlpha = breatheAlpha * fadeOut
            
            val baseColor = state.color
            
            // Draw glowing pill
            // Outer glow
            drawRoundRect(
                color = baseColor.copy(alpha = finalAlpha * 0.3f),
                topLeft = Offset(x - pillWidth / 2 - 8.dp.toPx(), currentY - pillHeight / 2 - 8.dp.toPx()),
                size = Size(pillWidth + 16.dp.toPx(), pillHeight + 16.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillWidth, pillWidth)
            )
            
            // Main pill
            drawRoundRect(
                color = baseColor.copy(alpha = finalAlpha),
                topLeft = Offset(x - pillWidth / 2, currentY - pillHeight / 2),
                size = Size(pillWidth, pillHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillWidth, pillWidth)
            )
            
            // Inner bright core
            drawRoundRect(
                color = Color.White.copy(alpha = finalAlpha * 0.5f),
                topLeft = Offset(x - pillWidth * 0.2f, currentY - pillHeight * 0.3f),
                size = Size(pillWidth * 0.4f, pillHeight * 0.6f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillWidth * 0.4f, pillWidth * 0.4f)
            )
        }
    }
}



/**
 * Helper function to get note name with octave.
 * Named uniquely to avoid clashes with other screens.
 */
private fun getNoteNameWithOctave(midiNote: Int): String {
    val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val octave = (midiNote / 12) - 1
    val note = midiNote % 12
    val noteName = noteNames[note]
    return "$noteName$octave"
}

/**
 * Settings Dialog for Superr Service
 */
@Composable
fun FreePlaySettingsDialog(
    sensitivity: Int,
    theme: Int,
    transpose: Int,
    onSensitivityChange: (Int) -> Unit,
    onThemeChange: (Int) -> Unit,
    onTransposeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Piano Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sensitivity
                Column {
                    Text(
                        text = "Sensitivity: $sensitivity",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = sensitivity.toFloat(),
                        onValueChange = { onSensitivityChange(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 99
                    )
                }
                
                // Theme
                Column {
                    Text(
                        text = "Theme: ${when(theme) {
                            0 -> "Aurora"
                            1 -> "Fire"
                            2 -> "Matrix"
                            else -> "Unknown"
                        }}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Aurora", "Fire", "Matrix").forEachIndexed { index, name ->
                            FilterChip(
                                selected = theme == index,
                                onClick = { onThemeChange(index) },
                                label = { Text(name) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                // Transpose
                Column {
                    Text(
                        text = "Transpose: ${if (transpose >= 0) "+" else ""}$transpose semitones",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = transpose.toFloat(),
                        onValueChange = { onTransposeChange(it.toInt()) },
                        valueRange = -12f..12f,
                        steps = 23
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

