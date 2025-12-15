package com.midi.pianomidi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midi.pianomidi.Note
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Component to display musical staff with notes and finger numbers
 */
@Composable
fun MusicalStaff(
    currentNote: Note?,
    notes: List<Note>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.White)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val staffLineSpacing = size.height / 6
            val staffStartY = staffLineSpacing
            val staffEndY = staffLineSpacing * 6
            val lineWidth = 2.dp.toPx()
            
            // Draw 5 staff lines
            for (i in 0..4) {
                val y = staffStartY + (i * staffLineSpacing)
                drawLine(
                    color = Color.Black,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = lineWidth
                )
            }
            
            // Draw treble clef (simplified as a symbol)
            val clefX = 20f
            val clefY = staffStartY + (staffLineSpacing * 2.5f)
            drawCircle(
                color = Color.Black,
                radius = 15f,
                center = Offset(clefX, clefY)
            )
            
            // Draw notes
            val noteSpacing = (size.width - 100f) / notes.size.coerceAtLeast(1)
            var noteX = 80f
            
            notes.forEachIndexed { index, note ->
                val noteY = getNoteYPosition(note.midiNote, staffStartY, staffLineSpacing)
                val isCurrentNote = currentNote?.midiNote == note.midiNote
                
                // Draw progress indicator (semi-transparent bar)
                if (isCurrentNote) {
                    drawRect(
                        color = Color.Gray.copy(alpha = 0.3f),
                        topLeft = Offset(noteX - 10f, 0f),
                        size = androidx.compose.ui.geometry.Size(30f, size.height)
                    )
                }
                
                // Draw note head
                drawCircle(
                    color = Color.Black,
                    radius = 8f,
                    center = Offset(noteX, noteY)
                )
                
                // Draw stem if needed (notes above middle line go down, below go up)
                val stemLength = 30f
                val stemDirection = if (noteY < staffStartY + (staffLineSpacing * 2.5f)) -1f else 1f
                drawLine(
                    color = Color.Black,
                    start = Offset(noteX + 8f, noteY),
                    end = Offset(noteX + 8f, noteY + (stemLength * stemDirection)),
                    strokeWidth = 2f
                )
                
                // Draw ledger lines for notes outside staff
                if (note.midiNote <= 60 || note.midiNote >= 79) {
                    val ledgerY = noteY
                    drawLine(
                        color = Color.Black,
                        start = Offset(noteX - 12f, ledgerY),
                        end = Offset(noteX + 12f, ledgerY),
                        strokeWidth = 1.5f
                    )
                }
                
                // Draw finger number above note
                val fingerNumber = getFingerNumber(note.midiNote, index)
                val fingerCircleY = noteY - 25f
                
                // Draw orange circle background
                drawCircle(
                    color = Color(0xFFFF9800), // Orange
                    radius = 12f,
                    center = Offset(noteX, fingerCircleY)
                )
                
                // Draw finger number text
                drawContext.canvas.nativeCanvas.apply {
                    val paint = Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 14.sp.toPx()
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawText(
                        fingerNumber.toString(),
                        noteX,
                        fingerCircleY + (paint.textSize / 3),
                        paint
                    )
                }
                
                noteX += noteSpacing
            }
            
            // Draw green dot for current position indicator
            currentNote?.let { note ->
                val dotX = 80f + (notes.indexOfFirst { it.midiNote == note.midiNote } * noteSpacing)
                val dotY = 20f
                drawCircle(
                    color = Color(0xFF4CAF50), // Green
                    radius = 6f,
                    center = Offset(dotX, dotY)
                )
            }
        }
    }
}

/**
 * Calculate Y position for a note on the staff
 * MIDI note 60 (C4) is middle C, positioned on a ledger line below the staff
 */
private fun getNoteYPosition(midiNote: Int, staffStartY: Float, lineSpacing: Float): Float {
    // C4 (60) = middle C = ledger line below staff
    // Each semitone moves up or down by half a line spacing
    val middleC = 60
    val middleCY = staffStartY + (lineSpacing * 3.5f) // Below third line
    
    val semitonesFromMiddleC = midiNote - middleC
    val yOffset = semitonesFromMiddleC * (lineSpacing / 2f)
    
    return middleCY - yOffset
}

/**
 * Get finger number for a note (simplified finger assignment)
 */
private fun getFingerNumber(midiNote: Int, index: Int): Int {
    // Simple finger assignment based on position in scale
    val fingerPattern = listOf(1, 2, 3, 2, 2, 2, 1, 5) // Common pattern
    return fingerPattern[index % fingerPattern.size]
}

