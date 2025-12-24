package com.midi.pianomidi

import androidx.compose.ui.graphics.Color
import com.midi.pianomidi.ui.theme.NeonGreen

/**
 * State representing a note being visualized (rising or falling)
 */
data class NoteVisualizerState(
    val note: Int,
    val velocity: Int,
    val startTime: Long,
    val color: Color = NeonGreen,
    val duration: Long = 0L // Optional: for falling notes with length
)

/**
 * Colors used for highlighting piano keys
 */
enum class KeyHighlightColor {
    NONE,
    NEON_GREEN, // Correct note (player)
    YELLOW,     // Active key (visualizer)
    RED,        // Incorrect note
    LIGHT_BLUE, // Target note (suggested)
    ORANGE      // Sustained/Special
}
