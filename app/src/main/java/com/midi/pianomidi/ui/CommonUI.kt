package com.midi.pianomidi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Data class for color flow particles
 */
data class ColorParticle(
    val x: Float,
    var y: Float,
    val color: Color,
    val velocity: Float,
    val size: Float,
    var alpha: Float,
    val startTime: Long
)

/**
 * Color flow particles animation - upward flowing colors when notes are played
 */
@Composable
fun ColorFlowParticles(
    particles: List<ColorParticle>,
    currentTime: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        particles.forEach { particle ->
            val elapsed = currentTime - particle.startTime
            if (elapsed >= 0) {
                // Draw particle with glow effect
                val particleColor = particle.color.copy(alpha = particle.alpha)
                
                // Outer glow
                drawCircle(
                    color = particle.color.copy(alpha = particle.alpha * 0.3f),
                    radius = particle.size * 1.5f,
                    center = Offset(particle.x, particle.y)
                )
                
                // Main particle
                drawCircle(
                    color = particleColor,
                    radius = particle.size,
                    center = Offset(particle.x, particle.y)
                )
                
                // Inner bright core
                drawCircle(
                    color = particle.color.copy(alpha = particle.alpha * 1.5f.coerceAtMost(1f)),
                    radius = particle.size * 0.5f,
                    center = Offset(particle.x, particle.y)
                )
            }
        }
    }
}

/**
 * Grid background pattern
 */
@Composable
fun GridBackgroundPattern(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val totalWhiteKeys = 14
        val gridSizeX = size.width / totalWhiteKeys
        val gridColor = Color(0xFF1A1A1A)
        
        // Draw vertical lines aligned with piano keys
        for (i in 0..totalWhiteKeys) {
            val x = i * gridSizeX
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }
        
        // Draw subtle horizontal lines
        val gridSizeY = 60.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += gridSizeY
        }
    }
}
