package com.midi.pianomidi.ui

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
    isPlaying: Boolean = false
) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Create references for constraint layout
        val (titleRef, connectButtonRef, statusRef, staffRef, keyboardRef, controlsRef, progressRef) = createRefs()
        
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
        
        // 4. Musical Staff (top half of screen)
        MusicalStaff(
            currentNote = currentNote,
            notes = songNotes.take(8), // Show first 8 notes
            modifier = Modifier
                .constrainAs(staffRef) {
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
            onNoteClick = onNoteClick, // Always allow virtual keyboard, even when MIDI device is connected
            modifier = Modifier
                .constrainAs(keyboardRef) {
                    top.linkTo(staffRef.bottom, margin = 8.dp)
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
 * Enum for key highlight colors
 */
enum class KeyHighlightColor {
    RED, YELLOW, LIGHT_BLUE, NONE
}

/**
 * Visual piano keyboard matching the image design
 * Features: 14 white keys, 10 black keys, black rounded frame, glossy white keys
 * Can be made interactive for virtual MIDI input
 * Supports multiple highlight colors (red, yellow, light blue, green, orange)
 * 
 * @param octaveStart MIDI note number for the starting C note (e.g., 24 for C1, 48 for C3)
 *                    The keyboard will display 2 octaves starting from this note
 */
@Composable
fun PianoKeyboard(
    currentNote: Int?,
    highlightedNotes: Map<Int, KeyHighlightColor> = emptyMap(),
    onNoteClick: ((Int) -> Unit)? = null,
    octaveStart: Int = 48, // Default to C3 (48)
    modifier: Modifier = Modifier
) {
    // Calculate white keys for 2 octaves starting from octaveStart
    // White keys pattern: C, D, E, F, G, A, B (repeated 2 times)
    val whiteKeys = remember(octaveStart) {
        val firstOctave = listOf(
            octaveStart,      // C
            octaveStart + 2,  // D
            octaveStart + 4,  // E
            octaveStart + 5,  // F
            octaveStart + 7,  // G
            octaveStart + 9,  // A
            octaveStart + 11  // B
        )
        val secondOctave = listOf(
            octaveStart + 12, // C
            octaveStart + 14, // D
            octaveStart + 16, // E
            octaveStart + 17, // F
            octaveStart + 19, // G
            octaveStart + 21, // A
            octaveStart + 23  // B
        )
        firstOctave + secondOctave
    }
    
    // Calculate black keys for 2 octaves
    // Black keys pattern: C#, D#, F#, G#, A# (repeated 2 times, no black keys after E and B)
    val blackKeys = remember(octaveStart) {
        val firstOctave = listOf(
            octaveStart + 1,  // C#
            octaveStart + 3,  // D#
            octaveStart + 6,  // F#
            octaveStart + 8,  // G#
            octaveStart + 10  // A#
        )
        val secondOctave = listOf(
            octaveStart + 13, // C#
            octaveStart + 15, // D#
            octaveStart + 18, // F#
            octaveStart + 20, // G#
            octaveStart + 22  // A#
        )
        firstOctave + secondOctave
    }
    
    // Helper function to get note label (without octave)
    fun getNoteLabel(midiNote: Int): String {
        val note = midiNote % 12
        return when (note) {
            0 -> "C"
            1 -> "C#"
            2 -> "D"
            3 -> "D#"
            4 -> "E"
            5 -> "F"
            6 -> "F#"
            7 -> "G"
            8 -> "G#"
            9 -> "A"
            10 -> "A#"
            11 -> "B"
            else -> ""
        }
    }
    
    // Helper function to get highlight color
    fun getKeyColor(note: Int, isBlack: Boolean): Color {
        val highlightColor = highlightedNotes[note]
        return when (highlightColor) {
            KeyHighlightColor.RED -> Color(0xFFF44336) // Red
            KeyHighlightColor.YELLOW -> Color(0xFFFFEB3B) // Yellow
            KeyHighlightColor.LIGHT_BLUE -> Color(0xFF81D4FA) // Light Blue
            KeyHighlightColor.NONE, null -> {
                if (currentNote == note) {
                    Color(0xFF4CAF50) // Green for current note (matching image)
                } else {
                    if (isBlack) Color(0xFF1A1A1A) else Color(0xFFFFFEFE)
                }
            }
        }
    }
    
    // Helper function to check if key is highlighted (for orange/green highlights)
    fun isKeyHighlighted(note: Int): Boolean {
        val highlightColor = highlightedNotes[note]
        return highlightColor != null && highlightColor != KeyHighlightColor.NONE
    }
    
    // Black frame with rounded corners (no outer gray border)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
                // White keys layer
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    whiteKeys.forEachIndexed { index, note ->
                        val highlightColor = highlightedNotes[note]
                        val isHighlighted = highlightColor != null && highlightColor != KeyHighlightColor.NONE
                        val isActive = currentNote == note || isHighlighted
                        val keyColor = getKeyColor(note, false)
                        
                        // Determine highlight overlay color
                        val overlayColor = when {
                            currentNote == note -> Color(0xFF4CAF50).copy(alpha = 0.9f) // Green
                            highlightColor == KeyHighlightColor.YELLOW -> Color(0xFFFFA500).copy(alpha = 0.7f) // Orange
                            else -> null
                        }
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (onNoteClick != null) {
                                        Modifier.clickable { onNoteClick(note) }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            // White key with rounded bottom edges and glossy effect
                            Canvas(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val keyWidth = size.width
                                val keyHeight = size.height
                                val cornerRadius = 8.dp.toPx()
                                
                                // Create rounded rectangle path for white key
                                val path = Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(keyWidth, 0f)
                                    lineTo(keyWidth, keyHeight - cornerRadius)
                                    arcTo(
                                        rect = androidx.compose.ui.geometry.Rect(
                                            offset = Offset(keyWidth - cornerRadius * 2, keyHeight - cornerRadius * 2),
                                            size = Size(cornerRadius * 2, cornerRadius * 2)
                                        ),
                                        startAngleDegrees = 0f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(cornerRadius, keyHeight)
                                    arcTo(
                                        rect = androidx.compose.ui.geometry.Rect(
                                            offset = Offset(0f, keyHeight - cornerRadius * 2),
                                            size = Size(cornerRadius * 2, cornerRadius * 2)
                                        ),
                                        startAngleDegrees = 90f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    close()
                                }
                                
                                // Draw white key with gradient for glossy effect
                                drawPath(
                                    path = path,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White,
                                            Color(0xFFF5F5F5),
                                            Color(0xFFE8E8E8)
                                        )
                                    )
                                )
                                
                                // Draw highlight overlay if active
                                overlayColor?.let { color ->
                                    drawPath(
                                        path = path,
                                        color = color
                                    )
                                }
                                
                                // Draw subtle border
                                drawPath(
                                    path = path,
                                    color = Color(0xFFCCCCCC),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }
                    }
                }
                
                // Black keys layer (positioned absolutely over white keys)
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val totalWidth = maxWidth
                    val totalHeight = maxHeight
                    
                    // Calculate positions for black keys relative to white keys
                    // Pattern: C-C#, D-D#, E-none, F-F#, G-G#, A-A#, B-none (repeats for 2 octaves)
                    val whiteKeyWidth = totalWidth / whiteKeys.size
                    // First octave: positions after C(0), D(1), F(3), G(4), A(5)
                    // Second octave: positions after C(7), D(8), F(10), G(11), A(12)
                    val blackKeyPositions = listOf(
                        whiteKeyWidth * 0.65f,  // C# after 1st C (index 0)
                        whiteKeyWidth * 1.65f,  // D# after 1st D (index 1)
                        whiteKeyWidth * 3.65f,  // F# after 1st F (index 3)
                        whiteKeyWidth * 4.65f,  // G# after 1st G (index 4)
                        whiteKeyWidth * 5.65f,  // A# after 1st A (index 5)
                        // Second octave positions
                        whiteKeyWidth * 7.65f,  // C# after 2nd C (index 7)
                        whiteKeyWidth * 8.65f,  // D# after 2nd D (index 8)
                        whiteKeyWidth * 10.65f, // F# after 2nd F (index 10)
                        whiteKeyWidth * 11.65f, // G# after 2nd G (index 11)
                        whiteKeyWidth * 12.65f  // A# after 2nd A (index 12)
                    )
                    
                    blackKeys.forEachIndexed { index, note ->
                        val highlightColor = highlightedNotes[note]
                        val isHighlighted = highlightColor != null && highlightColor != KeyHighlightColor.NONE
                        val isActive = currentNote == note || isHighlighted
                        
                        // Determine highlight overlay color
                        val overlayColor = when {
                            currentNote == note -> Color(0xFF4CAF50).copy(alpha = 0.9f) // Green
                            highlightColor == KeyHighlightColor.YELLOW -> Color(0xFFFFA500).copy(alpha = 0.7f) // Orange
                            else -> null
                        }
                        
                        val blackKeyWidth = whiteKeyWidth * 0.65f // Narrower than white keys
                        val blackKeyHeight = totalHeight * 0.65f // Shorter than white keys
                        
                        Box(
                            modifier = Modifier
                                .width(blackKeyWidth)
                                .height(blackKeyHeight)
                                .offset(x = blackKeyPositions[index], y = 0.dp)
                                .then(
                                    if (onNoteClick != null) {
                                        Modifier.clickable { onNoteClick(note) }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            // Black key with rounded bottom edges
                            Canvas(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val keyWidth = size.width
                                val keyHeight = size.height
                                val cornerRadius = 4.dp.toPx()
                                
                                // Create rounded rectangle path for black key
                                val path = Path().apply {
                                    moveTo(0f, 0f)
                                    lineTo(keyWidth, 0f)
                                    lineTo(keyWidth, keyHeight - cornerRadius)
                                    arcTo(
                                        rect = androidx.compose.ui.geometry.Rect(
                                            offset = Offset(keyWidth - cornerRadius * 2, keyHeight - cornerRadius * 2),
                                            size = Size(cornerRadius * 2, cornerRadius * 2)
                                        ),
                                        startAngleDegrees = 0f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(cornerRadius, keyHeight)
                                    arcTo(
                                        rect = androidx.compose.ui.geometry.Rect(
                                            offset = Offset(0f, keyHeight - cornerRadius * 2),
                                            size = Size(cornerRadius * 2, cornerRadius * 2)
                                        ),
                                        startAngleDegrees = 90f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    close()
                                }
                                
                                // Draw black key with subtle gradient
                                drawPath(
                                    path = path,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF1A1A1A),
                                            Color(0xFF0F0F0F),
                                            Color(0xFF000000)
                                        )
                                    )
                                )
                                
                                // Draw highlight overlay if active
                                overlayColor?.let { color ->
                                    drawPath(
                                        path = path,
                                        color = color
                                    )
                                }
                                
                                // Draw subtle border
                                drawPath(
                                    path = path,
                                    color = Color(0xFF000000),
                                    style = Stroke(width = 0.5.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }
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

