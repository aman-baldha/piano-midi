package com.midi.pianomidi.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.midi.pianomidi.Note
import com.midi.pianomidi.NoteVisualizerState
import com.midi.pianomidi.KeyHighlightColor
import com.midi.pianomidi.ui.theme.NeonGreen
import com.midi.pianomidi.ui.theme.DarkBackground
import com.midi.pianomidi.ui.theme.DarkSurface
import com.midi.pianomidi.ui.theme.TextPrimary
import com.midi.pianomidi.ui.theme.TextSecondary
import com.midi.pianomidi.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Main piano learning screen with all UI components
 */
@Composable
fun PianoLearningScreen(
    songTitle: String = "Twinkle Twinkle Little Star",
    isConnected: Boolean = false,
    isVirtualMode: Boolean = false,
    currentNote: Note? = null,
    songNotes: List<Note> = emptyList(),
    completedNotes: Int = 0,
    totalNotes: Int = 42,
    highlightedNotes: Map<Int, KeyHighlightColor> = emptyMap(),
    onConnectClick: () -> Unit = {},
    onStartClick: () -> Unit = {},
    onPauseClick: () -> Unit = {},
    onResetClick: () -> Unit = {},
    onNoteClick: ((Int) -> Unit)? = null,
    isPlaying: Boolean = false,
    onNavigateBack: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val horizontalPadding = 24.dp
    val coroutineScope = rememberCoroutineScope()
    var currentTime by remember { mutableStateOf(0L) }
    var waterfallNotes by remember { mutableStateOf<Map<Int, NoteVisualizerState>>(emptyMap()) }
    var currentOctaveStart by remember { mutableStateOf(48) } // Default C3
    
    // MIDI Output Handler
    val midiConnectionManager = com.midi.pianomidi.MidiConnectionManager.getInstance(androidx.compose.ui.platform.LocalContext.current)
    val rgbController = midiConnectionManager.getRgbController()

    // Update time for animations
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val baseTime = System.currentTimeMillis() - currentTime
            while (isPlaying) {
                currentTime = System.currentTimeMillis() - baseTime
                delay(16)
            }
        }
    }

    // Handle waterfall notes and MIDI highlighting
    LaunchedEffect(currentNote) {
        currentNote?.let { note ->
            // 1. Update visual waterfall
            val color = when ((note.midiNote) % 3) {
                0 -> Color(0xFFFF6B35) // Orange
                1 -> Color(0xFFFFEB3B) // Yellow
                else -> NeonGreen
            }
            waterfallNotes = waterfallNotes + (note.midiNote to NoteVisualizerState(
                note = note.midiNote,
                velocity = 100,
                startTime = System.currentTimeMillis(),
                color = color
            ))
            
            // 2. Highlight physical MIDI key
            rgbController?.highlightNote(note.midiNote, color)
            
            // Auto-remove after some time
            coroutineScope.launch {
                delay(note.duration + 500) // Keep highlighted for note duration
                waterfallNotes = waterfallNotes - note.midiNote
                rgbController?.clearNote(note.midiNote)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {

        ConstraintLayout(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = horizontalPadding, vertical = 8.dp)
        ) {
            // Create references for constraint layout
            val (titleRef, connectButtonRef, statusRef, visualizerRef, keyboardRef, controlsRef, progressRef) = createRefs()
        
        // 1. Song Title at top (with proper padding)
        Text(
            text = songTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .constrainAs(titleRef) {
                    top.linkTo(parent.top, margin = 16.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                }
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
        
        // 2. Connect MIDI Piano Button (with proper padding)
        Button(
            onClick = onConnectClick,
            modifier = Modifier
                .constrainAs(connectButtonRef) {
                    top.linkTo(titleRef.bottom, margin = 12.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                }
                .fillMaxWidth(0.5f)
                .padding(horizontal = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) MaterialTheme.colorScheme.secondary 
                                else MaterialTheme.colorScheme.primary
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = if (isConnected) "Disconnect MIDI" else "Connect MIDI Piano",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        
        // 3. Connection Status Indicator (with proper padding)
        ConnectionStatusIndicator(
            isConnected = isConnected,
            isVirtualMode = isVirtualMode,
            modifier = Modifier
                .constrainAs(statusRef) {
                    top.linkTo(connectButtonRef.bottom, margin = 12.dp)
                    start.linkTo(parent.start, margin = 16.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        
            // 4. Waterfall Visualizer (middle area)
            WaterfallVisualizer(
                songNotes = songNotes,
                currentTime = currentTime, // Current playback time in ms
                octaveStart = currentOctaveStart,
                isPlaying = isPlaying,
                modifier = Modifier
                    .constrainAs(visualizerRef) {
                        top.linkTo(statusRef.bottom, margin = 8.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(keyboardRef.top)
                        height = Dimension.fillToConstraints
                        width = Dimension.fillToConstraints
                    }
                    .fillMaxWidth()
            )
            
            // 5. Visual Piano Keyboard (bottom half, larger)
            PianoKeyboard(
                currentNote = currentNote?.midiNote,
                highlightedNotes = highlightedNotes,
                onNoteClick = onNoteClick,
                octaveStart = currentOctaveStart,
                modifier = Modifier
                    .constrainAs(keyboardRef) {
                        top.linkTo(visualizerRef.bottom, margin = 8.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(controlsRef.top, margin = 8.dp)
                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    }
                    .fillMaxWidth()
            )
        
        // 6. Controls (bottom left - pause button)
        Row(
            modifier = Modifier
                .constrainAs(controlsRef) {
                    bottom.linkTo(parent.bottom, margin = 8.dp)
                    start.linkTo(parent.start, margin = 8.dp)
                }
        ) {
            // Pause/Play button
            FloatingActionButton(
                onClick = if (isPlaying) onPauseClick else onStartClick,
                modifier = Modifier.size(56.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Start",
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Reset button
            FloatingActionButton(
                onClick = onResetClick,
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // 6. Progress Indicator (bottom right)
        ProgressIndicator(
            completed = completedNotes,
            total = totalNotes,
            modifier = Modifier
                .constrainAs(progressRef) {
                    bottom.linkTo(parent.bottom, margin = 8.dp)
                    end.linkTo(parent.end, margin = 8.dp)
                    start.linkTo(controlsRef.end, margin = 16.dp)
                    width = Dimension.fillToConstraints
                }
                .fillMaxWidth(0.5f)
        )
        }
    }
}

/**
 * Connection status indicator showing connected/disconnected state
 */
@Composable
fun ConnectionStatusIndicator(
    isConnected: Boolean,
    isVirtualMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        val statusColor = when {
            isVirtualMode -> Color(0xFF9C27B0) // Purple for virtual mode
            isConnected -> Color(0xFF4CAF50) // Green for connected
            else -> Color(0xFFF44336) // Red for disconnected
        }
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        val statusText = when {
            isVirtualMode -> "Virtual Mode (Tap keys to play)"
            isConnected -> "Connected"
            else -> "Disconnected"
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor
        )
    }
}






/**
 * Large display showing the current note to play
 */
@Composable
fun CurrentNoteDisplay(
    currentNote: Note?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Current Note",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentNote?.let { getNoteName(it.midiNote) } ?: "--",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (currentNote != null) {
                Text(
                    text = "MIDI: ${currentNote.midiNote}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Playback controls: Start, Pause, Reset buttons
 */
@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    onStartClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Start/Pause button
        FloatingActionButton(
            onClick = if (isPlaying) onPauseClick else onStartClick,
            modifier = Modifier.size(64.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Start",
                modifier = Modifier.size(32.dp)
            )
        }
        
        // Reset button
        FloatingActionButton(
            onClick = onResetClick,
            modifier = Modifier.size(56.dp),
            containerColor = MaterialTheme.colorScheme.secondary
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Progress indicator showing completed notes
 */
@Composable
fun ProgressIndicator(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Progress: $completed / $total notes",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) completed.toFloat() / total else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

/**
 * Convert MIDI note number to note name (e.g., 60 -> "C4")
 */
fun getNoteName(midiNote: Int): String {
    val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val octave = (midiNote / 12) - 1
    val note = midiNote % 12
    val noteName = noteNames[note]
    return "$noteName$octave"
}
/**
 * Waterfall Visualizer component for falling notes
 */
@Composable
fun WaterfallVisualizer(
    songNotes: List<com.midi.pianomidi.Note>,
    currentTime: Long,
    octaveStart: Int = 48,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val whiteKeyWidthPx = size.width / 14
        val leadTime = 3000L // 3 seconds of notes shown on screen
        
        // Filter notes that are within the visible window (now to future)
        // Or slightly in the past to show them "hitting" the keys
        val visibleNotes = songNotes.filter { 
            it.timing >= currentTime - 500 && it.timing <= currentTime + leadTime 
        }
        
        visibleNotes.forEach { note ->
            val relativeTiming = note.timing - currentTime
            
            // Y position: 0 is top, height is hit line (keys)
            // leadTime corresponds to height
            val progress = 1f - (relativeTiming.toFloat() / leadTime)
            val currentY = progress * size.height
            
            // Map note to horizontal position
            val octaveNote = note.midiNote % 12
            val relativeNote = note.midiNote - octaveStart
            val octaveIndex = relativeNote / 12
            
            val x = when (octaveNote) {
                0 -> (octaveIndex * 7 + 0.5f) * whiteKeyWidthPx // C
                2 -> (octaveIndex * 7 + 1.5f) * whiteKeyWidthPx // D
                4 -> (octaveIndex * 7 + 2.5f) * whiteKeyWidthPx // E
                5 -> (octaveIndex * 7 + 3.5f) * whiteKeyWidthPx // F
                7 -> (octaveIndex * 7 + 4.5f) * whiteKeyWidthPx // G
                9 -> (octaveIndex * 7 + 5.5f) * whiteKeyWidthPx // A
                11 -> (octaveIndex * 7 + 6.5f) * whiteKeyWidthPx // B
                1 -> (octaveIndex * 7 + 1.0f) * whiteKeyWidthPx // C#
                3 -> (octaveIndex * 7 + 2.0f) * whiteKeyWidthPx // D#
                6 -> (octaveIndex * 7 + 4.0f) * whiteKeyWidthPx // F#
                8 -> (octaveIndex * 7 + 5.0f) * whiteKeyWidthPx // G#
                10 -> (octaveIndex * 7 + 6.0f) * whiteKeyWidthPx // A#
                else -> (octaveIndex * 7 + 0.5f) * whiteKeyWidthPx
            }
            
            // Draw pill shape
            val pillWidth = 20.dp.toPx()
            // Height based on duration
            val pillHeight = (note.duration.toFloat() / leadTime) * size.height
            
            val baseColor = when (octaveNote) {
                0, 4, 7 -> NeonGreen
                2, 5, 9 -> Color(0xFFFFEB3B) // Yellow
                else -> Color(0xFFFF6B35) // Orange
            }
            
            val alpha = if (relativeTiming < 0) 1f + (relativeTiming / 500f) else 1f
            val finalAlpha = alpha.coerceIn(0f, 1f)

            if (currentY > -pillHeight && currentY < size.height + pillHeight) {
                // Glow
                drawRoundRect(
                    color = baseColor.copy(alpha = finalAlpha * 0.3f),
                    topLeft = Offset(x - pillWidth * 0.75f, currentY - pillHeight - 5.dp.toPx()),
                    size = Size(pillWidth * 1.5f, pillHeight + 10.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillWidth, pillWidth)
                )
                
                // Main pill
                drawRoundRect(
                    color = baseColor.copy(alpha = finalAlpha),
                    topLeft = Offset(x - pillWidth / 2, currentY - pillHeight),
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillWidth, pillWidth)
                )
            }
        }
    }
}

