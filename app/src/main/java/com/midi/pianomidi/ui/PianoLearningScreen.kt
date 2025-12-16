package com.midi.pianomidi.ui

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
import androidx.compose.ui.graphics.Color
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
            .padding(8.dp)
    ) {
        // Create references for constraint layout
        val (titleRef, connectButtonRef, statusRef, staffRef, keyboardRef, controlsRef, progressRef) = createRefs()
        
        // 1. Song Title at top (smaller, compact)
        Text(
            text = songTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .constrainAs(titleRef) {
                    top.linkTo(parent.top, margin = 8.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
                .fillMaxWidth()
        )
        
        // 2. Connect MIDI Piano Button
        Button(
            onClick = onConnectClick,
            modifier = Modifier
                .constrainAs(connectButtonRef) {
                    top.linkTo(titleRef.bottom, margin = 8.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
                .fillMaxWidth(0.5f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) MaterialTheme.colorScheme.secondary 
                                else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isConnected) "Disconnect MIDI" else "Connect MIDI Piano",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        // 3. Connection Status Indicator (compact)
        ConnectionStatusIndicator(
            isConnected = isConnected,
            isVirtualMode = isVirtualMode,
            modifier = Modifier
                .constrainAs(statusRef) {
                    top.linkTo(connectButtonRef.bottom, margin = 4.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
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
 * Visual piano keyboard showing notes from C4 to C6
 * White keys and black keys are represented as colored rectangles with labels
 * Can be made interactive for virtual MIDI input
 * Supports multiple highlight colors (red, yellow, light blue)
 */
@Composable
fun PianoKeyboard(
    currentNote: Int?,
    highlightedNotes: Map<Int, KeyHighlightColor> = emptyMap(),
    onNoteClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // MIDI notes from C4 (60) to C6 (84) - 3 octaves
    // White keys: C, D, E, F, G, A, B (repeated 3 times)
    val whiteKeys = listOf(60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79, 81, 83, 84)
    
    // Helper function to get note label (without octave)
    fun getNoteLabel(midiNote: Int): String {
        val note = midiNote % 12
        return when (note) {
            0 -> "C"
            1 -> "Db"
            2 -> "D"
            3 -> "Eb"
            4 -> "E"
            5 -> "F"
            6 -> "Gb"
            7 -> "G"
            8 -> "Ab"
            9 -> "A"
            10 -> "Bb"
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
                    Color(0xFF2196F3) // Blue for current note
                } else {
                    if (isBlack) Color(0xFF212121) else Color.White
                }
            }
        }
    }
    
    // Helper function to get border color
    fun getBorderColor(note: Int, isBlack: Boolean): Color {
        val highlightColor = highlightedNotes[note]
        return when (highlightColor) {
            KeyHighlightColor.RED -> Color(0xFFD32F2F)
            KeyHighlightColor.YELLOW -> Color(0xFFFBC02D)
            KeyHighlightColor.LIGHT_BLUE -> Color(0xFF4FC3F7)
            KeyHighlightColor.NONE, null -> {
                if (currentNote == note) {
                    Color(0xFF1976D2)
                } else {
                    if (isBlack) Color.Black else Color(0xFFBDBDBD)
                }
            }
        }
    }
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE0E0E0))
            .padding(4.dp)
    ) {
        // White keys layer
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            whiteKeys.forEachIndexed { index, note ->
                val highlightColor = highlightedNotes[note]
                val isHighlighted = highlightColor != null && highlightColor != KeyHighlightColor.NONE
                val isActive = currentNote == note || isHighlighted
                val keyColor = getKeyColor(note, false)
                val borderColor = getBorderColor(note, false)
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(keyColor)
                        .border(
                            width = if (isActive) 3.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                        )
                        .then(
                            if (onNoteClick != null) {
                                Modifier.clickable { onNoteClick(note) }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Show note label at bottom of white key
                    Text(
                        text = getNoteLabel(note),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive && !isHighlighted) Color.White else Color.Black,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
        
        // Black keys layer (positioned absolutely over white keys)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 0.dp, end = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            whiteKeys.forEachIndexed { index, whiteNote ->
                // Determine if there's a black key after this white key
                // Pattern: C-C#, D-D#, E-none, F-F#, G-G#, A-A#, B-none
                val positionInOctave = index % 7
                val blackNote = when (positionInOctave) {
                    0 -> whiteNote + 1  // C#/Db after C
                    1 -> whiteNote + 1  // D#/Eb after D
                    3 -> whiteNote + 1  // F#/Gb after F
                    4 -> whiteNote + 1  // G#/Ab after G
                    5 -> whiteNote + 1  // A#/Bb after A
                    else -> null        // No black key after E or B
                }
                
                Box(modifier = Modifier.weight(1f)) {
                    if (blackNote != null && blackNote <= 84) {
                        val highlightColor = highlightedNotes[blackNote]
                        val isHighlighted = highlightColor != null && highlightColor != KeyHighlightColor.NONE
                        val isActive = currentNote == blackNote || isHighlighted
                        val keyColor = getKeyColor(blackNote, true)
                        val borderColor = getBorderColor(blackNote, true)
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .fillMaxHeight(0.65f)
                                .align(Alignment.TopEnd)
                                .padding(end = 2.dp)
                                .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(keyColor)
                                .border(
                                    width = if (isActive) 2.dp else 1.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
                                )
                                .then(
                                    if (onNoteClick != null) {
                                        Modifier.clickable { onNoteClick(blackNote) }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Show note label at bottom of black key
                            Text(
                                text = getNoteLabel(blackNote),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive && !isHighlighted) Color.White else Color.White,
                                modifier = Modifier.padding(bottom = 2.dp)
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

