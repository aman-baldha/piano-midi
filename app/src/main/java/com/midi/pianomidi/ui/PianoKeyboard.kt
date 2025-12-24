package com.midi.pianomidi.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.midi.pianomidi.KeyHighlightColor
import com.midi.pianomidi.ui.theme.NeonGreen

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
    
    // Breathing animation for highlight
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breatheAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    )
    
    // Black frame
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
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
                whiteKeys.forEach { note ->
                    val highlightColor = highlightedNotes[note]
                    val isHighlighted = highlightColor != null && highlightColor != KeyHighlightColor.NONE
                    
                    val overlayColor = when {
                        currentNote == note -> NeonGreen.copy(alpha = 0.8f * breatheAlpha)
                        highlightColor == KeyHighlightColor.YELLOW -> Color(0xFFFFEB3B).copy(alpha = 0.7f * breatheAlpha)
                        highlightColor == KeyHighlightColor.RED -> Color(0xFFF44336).copy(alpha = 0.7f * breatheAlpha)
                        highlightColor == KeyHighlightColor.LIGHT_BLUE -> Color(0xFF81D4FA).copy(alpha = 0.7f * breatheAlpha)
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
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val keyWidth = size.width
                            val keyHeight = size.height
                            val cornerRadius = 12.dp.toPx()
                            
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
                            
                            drawPath(
                                path = path,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.White, Color(0xFFF5F5F5), Color(0xFFE0E0E0))
                                )
                            )
                            
                            overlayColor?.let { color ->
                                drawPath(path = path, color = color)
                            }
                        }
                    }
                }
            }
            
            // Black keys layer
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val whiteKeyWidth = maxWidth / whiteKeys.size
                val blackKeyPositions = listOf(
                    whiteKeyWidth * 0.7f, whiteKeyWidth * 1.7f, 
                    whiteKeyWidth * 3.7f, whiteKeyWidth * 4.7f, whiteKeyWidth * 5.7f,
                    whiteKeyWidth * 7.7f, whiteKeyWidth * 8.7f, 
                    whiteKeyWidth * 10.7f, whiteKeyWidth * 11.7f, whiteKeyWidth * 12.7f
                )
                
                blackKeys.forEachIndexed { index, note ->
                    val highlightColor = highlightedNotes[note]
                    val overlayColor = when {
                        currentNote == note -> NeonGreen.copy(alpha = 0.8f * breatheAlpha)
                        highlightColor == KeyHighlightColor.YELLOW -> Color(0xFFFFEB3B).copy(alpha = 0.7f * breatheAlpha)
                        highlightColor == KeyHighlightColor.RED -> Color(0xFFF44336).copy(alpha = 0.7f * breatheAlpha)
                        else -> null
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(whiteKeyWidth * 0.6f)
                            .height(maxHeight * 0.62f)
                            .offset(x = blackKeyPositions[index], y = 0.dp)
                            .then(
                                if (onNoteClick != null) {
                                    Modifier.clickable { onNoteClick(note) }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cornerRadius = 6.dp.toPx()
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width, size.height - cornerRadius)
                                arcTo(
                                    rect = androidx.compose.ui.geometry.Rect(
                                        offset = Offset(size.width - cornerRadius * 2, size.height - cornerRadius * 2),
                                        size = Size(cornerRadius * 2, cornerRadius * 2)
                                    ),
                                    startAngleDegrees = 0f,
                                    sweepAngleDegrees = 90f,
                                    forceMoveTo = false
                                )
                                lineTo(cornerRadius, size.height)
                                arcTo(
                                    rect = androidx.compose.ui.geometry.Rect(
                                        offset = Offset(0f, size.height - cornerRadius * 2),
                                        size = Size(cornerRadius * 2, cornerRadius * 2)
                                    ),
                                    startAngleDegrees = 90f,
                                    sweepAngleDegrees = 90f,
                                    forceMoveTo = false
                                )
                                close()
                            }
                            
                            drawPath(
                                path = path,
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF333333), Color(0xFF1A1A1A), Color(0xFF000000))
                                )
                            )
                            
                            overlayColor?.let { color ->
                                drawPath(path = path, color = color)
                            }
                        }
                    }
                }
            }
        }
    }
}
